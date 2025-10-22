package rookie.servicedingbot.utils

import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest
import com.aliyun.dingtalkrobot_1_0.Client
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTOHeaders
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTORequest
import com.aliyun.tea.TeaException
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import rookie.servicedingbot.controller.DingBotMessageHandler

@Component
class DingtalkUtils(
    val okHttpClient: OkHttpClient,
    val jsonSerializer: Json
) {
    companion object{
        val logger: Logger? = LoggerFactory.getLogger(DingtalkUtils::class.java)

    }

    @Value("\${dingtalk.simpleCallback}")
    private val simpleCallback: String=""
    @Value("\${dingtalk.app.SuiteKey}")
    lateinit var suiteKey: String

    @Value("\${dingtalk.app.SuiteSecret}")
    lateinit var suiteSecret: String

    @Value("\${dingtalk.app.topic}")
    var topic: String="/v1.0/im/bot/messages/get"
    var currentModel="deepseek-chat"

    @Value("\${dingtalk.app.robotCode}")
    var robotCode="dingxpnkaa8gqjljr78l"



    suspend fun fastMessageSendWithCallBackUrl(message: String): Boolean {
        val jsonObject : JsonObject= buildJsonObject {
            put("msgtype", "text")
            putJsonObject("text") {
                put("content", "[Log]$message")
            }
        }
        val jsonString = jsonSerializer.encodeToString(JsonObject.serializer(), jsonObject)


        val request = Request.Builder()
            .url(simpleCallback)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Go-Script/1.0")
            .post(jsonString.toRequestBody())
            .build()

        okHttpClient.newCall(request).execute().use{ resp->
            if(!resp.isSuccessful){
                logger?.error("send message failed: ${resp.code} ${resp.message}")
                return false
            }
            return true
        }

    }
    fun fastMessageSendToUser(message:String,userId:String){
        val client: Client = createClient()
        var otoHeaders = BatchSendOTOHeaders()
        otoHeaders.setXAcsDingtalkAccessToken(getToken())

        val otoRequest = BatchSendOTORequest()
            .setMsgKey("sampleText")
            .setMsgParam("{\"content\":\"${message}\"}")
            .setRobotCode(robotCode)
            .setUserIds(listOf(userId))

        try {
            client.batchSendOTOWithOptions(otoRequest, otoHeaders, RuntimeOptions())
        }catch (e: TeaException){
            logger?.error("主动向用户发送消息 调用失败: ${e.message}")
        }catch (e: Exception){
            logger?.error("主动向用户发送消息 执行异常: ${e.message}")
        }

    }

    @Throws(java.lang.Exception::class)
    fun createClient(): Client {
        val config = Config()
        config.protocol = "https"
        config.regionId = "central"
        return Client(config)
    }



    fun getToken(): String {
        val tokenRequest = GetAccessTokenRequest()
        tokenRequest.setAppKey(suiteKey)
        tokenRequest.setAppSecret(suiteSecret)
        val config = Config()
        config.protocol = "https"
        config.regionId = "central"
        try {
            val client = com.aliyun.dingtalkoauth2_1_0.Client(config)
            val accessToken = client.getAccessToken(tokenRequest)
            return accessToken.body.accessToken ?: ""
        } catch (e: Exception) {
            DingBotMessageHandler.Companion.logger.error("获取钉钉 access_token 失败 ${e.message}", e)
            return ""
        }
    }

}