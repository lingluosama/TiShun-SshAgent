package rookie.servicedingbot.service.impl

import com.mybatisflex.core.query.QueryChain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.mybatisflex.spring.service.impl.ServiceImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.java.net.cookiejar.JavaNetCookieJar
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

import rookie.servicedingbot.mapper.ForumAccountMapper
import rookie.servicedingbot.model.consts.BusinessException
import rookie.servicedingbot.model.entity.ForumAccount
import rookie.servicedingbot.service.ForumService
import rookie.servicedingbot.utils.SimpleMessageSender
import java.io.InputStream
import rookie.servicedingbot.model.entity.table.ForumAccountTableDef.FORUM_ACCOUNT
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.atomic.AtomicInteger

@Service
class ForumServiceImpl(
    val okHttpClient: OkHttpClient,
    val simpleMessageSender: SimpleMessageSender
) : ServiceImpl<ForumAccountMapper, ForumAccount>(), ForumService{


    /**
     * 获取formhash验证
     */
    val loginPageURL: String="https://www.51fuman.com/member.php?mod=logging&action=login"
    val loginURL: String="https://www.51fuman.com/member.php?mod=logging&action=login&loginsubmit=yes&infloat=yes&lssubmit=yes&inajax=1"
    val signPageURL: String="https://www.51fuman.com/plugin.php?id=dsu_paulsign:sign"
    val signURL: String="https://www.51fuman.com/plugin.php?id=dsu_paulsign:sign&operation=qiandao&infloat=1&inajax=1"

    companion object{
        private val logger = LoggerFactory.getLogger(ForumServiceImpl::class.java)
    }



    override suspend fun processSchedule(): Boolean {

        val queryChain = QueryChain.of<ForumAccount>(this.mapper)
            .where { FORUM_ACCOUNT.DISABLED.eq(false) }
            .orderBy(FORUM_ACCOUNT.ID.desc())
        val result = withContext(Dispatchers.IO) {
            mapper.selectListByQuery(queryChain)
        }

        val successCount = AtomicInteger(0)

        //限定协程作用域，保证并发任务的原子性以使用async时挂起当前线程
        coroutineScope {
            result.map { account ->
                async {
                    val username = account.username
                    val password = account.password
                    if (username == null || password == null) {
                        return@async "Skipped: Account with null credentials."
                    }
                    try {
                        val client = login(username, password)

                        val success = sign(client)

                        if (success == true)successCount.incrementAndGet()
                    } catch (e: BusinessException) {
                        // 捕获任何网络或登录异常
                        logger.info("错误: 账号 $username 签到失败: ${e.message}")
                    }catch (e: Exception){
                        logger.error("严重错误: 账号 $username 发生未预期错误", e)
                    }
                }
            }.awaitAll()
        }
        logger.info("$successCount 个账号已经完成签到")
        return true
    }

    override suspend fun login(username: String, password: String): OkHttpClient {

        //创建独立的cookieJar
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy ( CookiePolicy.ACCEPT_ALL )
        val isolatedCookieJar= JavaNetCookieJar(cookieManager)


        val session = okHttpClient.newBuilder()
            .cookieJar(isolatedCookieJar)
            .build()

        val pageReq = Request.Builder()
            .url(loginPageURL)
            .get()
            .build()

        val formHash = session.newCall(pageReq).execute().use { response ->
            if (response.isSuccessful) {
                try {
                    return@use getFormHash(response.body.byteStream())
                } catch (e: Exception) {
                    throw BusinessException(message = "提取formHash失败", exception = e)
                }
            } else {
                throw BusinessException(code = 500, message = "获取页面formHash失败")
            }
        }

        val loginFormBody = FormBody.Builder()
            .add("fastloginfield", "username")
            .add("username", username)
            .add("password", password)
            .add("formhash", formHash)
            .add("quickforward", "yes")
            .add("handlekey", "ls")
            .build()
        val request = Request.Builder()
            .url(loginURL)
            .post(loginFormBody)
            .build()

        session.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body.string()
                if (body.contains("登录失败")) {
                    throw BusinessException(code = 500, message = "登录失败，账号或密码错误")
                } else {
                    //挂起当前调用，避免阻塞整个process线程
                    logger.info("登录成功: 账号 $username,响应信息: $body")
                    return withContext(Dispatchers.IO){
                        session
                    }
                }
            } else {
                throw BusinessException(code = 500, message = "登录失败,获取响应失败")
            }
        }


    }

    override suspend fun sign(client: OkHttpClient): Boolean {
        val request = Request.Builder()
            .url(signPageURL)
            .get()
            .build()
        val formHash = client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                return@use getFormHash(response.body.byteStream())
            } else {
                throw BusinessException(code = 500, message = "获取页面formHash失败")
            }
        }


        val loginForm = FormBody.Builder()
            .add("formhash", formHash)
            .add("qdxq", "kx").build()

        val loginRequest = Request.Builder()
            .url(signURL)
            .post(loginForm)
            .build()

        client.newCall(loginRequest).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body.string()
                if (body.contains("签到成功")) {
                    simpleMessageSender.send(body)
                    return withContext(Dispatchers.IO){true}
                } else {
                    simpleMessageSender.send(body)
                    return withContext(Dispatchers.IO){false}
                }
            } else {
                throw BusinessException(code = 500, message = "签到失败,获取响应失败")
            }
        }



    }

    override suspend fun addAccount(account: ForumAccount): Boolean {
        logger.info("添加账号: $account")
        return withContext(Dispatchers.IO){
            this@ForumServiceImpl.save( account)
        }
    }

    private fun getFormHash(body: InputStream):String{
        val document = Jsoup.parse(body, "UTF-8", "")
        //css选择器秒了
        val formElement =
            document.select("input[name=formhash]").first() ?: throw NoSuchElementException("未找到formhash元素")
        return formElement.attr("value")

    }

}