package rookie.servicedingbot.service.impl

import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTOHeaders
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTORequest
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendHeaders
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendRequest
import com.aliyun.tea.TeaException
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.aspectj.bridge.Message
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import rookie.servicedingbot.model.form.DingTalkMessage
import rookie.servicedingbot.model.form.SampleChatForm
import rookie.servicedingbot.service.ChatAgentService
import rookie.servicedingbot.service.DingBotCommandService
import rookie.servicedingbot.service.HistoryService
import rookie.servicedingbot.utils.DingtalkUtils
import shade.com.alibaba.fastjson2.JSONObject
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class DingBotCommandServiceImpl(
    val dingtalkUtils: DingtalkUtils,
    val historyService: HistoryService,
    val chatAgentService: ChatAgentService,
    val jsonSerializer: Json,
    val redissonClient: RedissonClient
) : DingBotCommandService {

    companion object {
        val logger = LoggerFactory.getLogger(DingBotCommandServiceImpl::class.java)
        private val HELP_DOCUMENTATION = """
            
            在下可以帮主人做这些事情：
            
            1. 默认聊天: 主人直接吩咐就好，我已经连接到主人的服务器了汪。
            
            2. 切换模型:
               - **命令**: /change [模型名称]
               - **示例**: /change gemini-pro
               - **功能**: 切换当前会话使用的 LLM 模型。
               
            3. 清除历史记录:
               - **命令**: /clean
               - **功能**: 清除当前会话或群聊的历史记录，重置上下文。
               
            4. 获取帮助:
               - **命令**: /help
               - **功能**: 显示此帮助文档。
               
            当前使用的模型是: deepseek-chat
            
            ----------------------------
        """.trimIndent()
        private const val CHAT_LOCK_WAITING_SECONDS: Long = 0
        private const val CHAT_LOCK_TIMEOUT_SECONDS: Long = -1
        val chatIsBusy ="主人，我还在正在处理中，等在下一下吧QAQ"

    }

    @Value("\${dingtalk.app.SuiteKey}")
    lateinit var suiteKey: String

    @Value("\${dingtalk.app.SuiteSecret}")
    lateinit var suiteSecret: String

    @Value("\${dingtalk.app.topic}")
    var topic: String="/v1.0/im/bot/messages/get"
    var currentModel="deepseek-chat"
    override fun chatWithAgent(message: DingTalkMessage): JSONObject {
        val userId = message.senderStaffId.toString()
        val userMessage = message.text.content
        val conversationId = message.conversationId
        val robotCode = message.robotCode
        val replay = JSONObject()

        val historyKey=if(message.conversationType!="1")conversationId else userId

        val chatLock = redissonClient.getLock("chatting:$historyKey")
        val isGetLock = chatLock.tryLock(CHAT_LOCK_WAITING_SECONDS, CHAT_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)


        val chatForm = SampleChatForm(
            model = currentModel,
            userMessage,
            historyKey,
            message.conversationType!="1"
            )

        try {
            if(isGetLock){
                val chatReply = runBlocking {
                    val flux: Flux<String> = chatAgentService.sampleChat(chatForm)

                    flux
                        .map { it.trim() }
                        .reduce (""){ accumulator, item -> accumulator + item }
                        .block(Duration.ofSeconds(120))
                        ?: "" // 如果返回 null，则使用空字符串
                }
                replay["content"] = chatReply

                //存入消息历史
                val historyJson = buildJsonObject {
                    put("userMessage", userMessage)
                    put("agentReply", chatReply)
                }
                val historyJsonString = jsonSerializer.encodeToString(JsonObject.serializer(), historyJson)

                if(!chatReply.isEmpty())historyService.addHistory(conversationId,historyJsonString)
            }else{
                replay["content"] =chatIsBusy
            }
        }catch (e: Exception){
            replay["content"] = "对不起老公我鼠了:${e.message}"
        }catch (e: IllegalMonitorStateException){
            replay["content"] =chatIsBusy
        }finally {
            if(chatLock.isHeldByCurrentThread)
                chatLock.unlock()
        }
        val config = Config()
        config.protocol = "https"
        config.regionId = "central"
        try {
            return when (message.conversationType) {
                "1" -> sendSingleMessage(robotCode, userId,replay.toJSONString()) // 群聊
                "2" -> sendGroupMessage(robotCode, conversationId,replay.toJSONString())      // 单聊
                else -> {
                    logger.warn("未知的会话类型: {}", message.conversationType)
                    JSONObject()
                }
            }
        } catch (e: TeaException) {
            logger.error("机器人API交互错误, errCode={}, errorMessage={}", e.getCode(), e.message, e)
            throw e
        } catch (e: Exception) {
            logger.error("机器人消息发送业务异常, errorMessage={}", e.message, e)
            throw e
        }

    }

    override fun changeModel(message: DingTalkMessage): JSONObject {
        val userMessage = message.text.content
        val conversationId = message.conversationId
        val robotCode = message.robotCode
        val userId = message.senderStaffId.toString()

        val modelName = userMessage.removePrefix("/change").trim()
        val replay = JSONObject()

        if (modelName.isEmpty()) {
            replay["content"] = "请指定要切换的模型名称,格式应为: **/change [模型名称]**"
        } else {
            // 假设您在其他地方有一个可用模型的列表，这里简化为直接设置
            this.currentModel = modelName
            replay["content"] = "✅ 模型已成功切换为: $modelName**"
        }

        try {
            return when (message.conversationType) {
                "1" -> sendSingleMessage(robotCode, userId,replay.toJSONString()) // 群聊
                "2" -> sendGroupMessage(robotCode, conversationId,replay.toJSONString())      // 单聊
                else -> {
                    logger.warn("未知的会话类型: {}", message.conversationType)
                    JSONObject()
                }
            }
        } catch (e: TeaException) {
            logger.error("机器人API交互错误, errCode={}, errorMessage={}", e.getCode(), e.message, e)
            throw e
        } catch (e: Exception) {
            logger.error("机器人消息发送业务异常, errorMessage={}", e.message, e)
            throw e
        }

    }

    override fun getHelpDoc(message: DingTalkMessage): JSONObject {
        val robotCode = message.robotCode
        val conversationId = message.conversationId
        val userId = message.senderStaffId.toString()

        // 更新当前模型信息
        val helpContent = HELP_DOCUMENTATION.replace("deepseek-chat", this.currentModel)

        val replay = JSONObject().apply {
            put("content", helpContent)
        }

        try {
            return when (message.conversationType) {
                "1" -> sendSingleMessage(robotCode, userId, replay.toJSONString()) // 群聊
                "2" -> sendGroupMessage(robotCode, conversationId, replay.toJSONString())      // 单聊
                else -> {
                    logger.warn("未知的会话类型: {}", message.conversationType)
                    JSONObject()
                }
            }
        } catch (e: TeaException) {
            logger.error("机器人API交互错误, errCode={}, errorMessage={}", e.getCode(), e.message, e)
            throw e
        } catch (e: Exception) {
            logger.error("机器人消息发送业务异常, errorMessage={}", e.message, e)
            throw e
        }
    }

    override fun cleanChatHistory(message: DingTalkMessage): JSONObject {
        val userId = message.senderStaffId.toString()
        val userMessage = message.text.content
        val conversationId = message.conversationId
        val robotCode = message.robotCode
        val replay = JSONObject()

        if(message.conversationType!="1"){
            historyService.deleteHistory(conversationId)
        }else{
            historyService.deleteHistory(userId)
        }
        replay["content"] = "已清空历史记录,上下文已重置"

        try {
            return when (message.conversationType) {
                "1" -> sendSingleMessage(robotCode, userId,replay.toJSONString()) // 群聊
                "2" -> sendGroupMessage(robotCode, conversationId,replay.toJSONString())      // 单聊
                else -> {
                    logger.warn("未知的会话类型: {}", message.conversationType)
                    JSONObject()
                }
            }
        }  catch (e: TeaException) {
            logger.error("机器人API交互错误, errCode={}, errorMessage={}", e.getCode(), e.message, e)
            throw e
        } catch (e: Exception) {
            logger.error("机器人消息发送业务异常, errorMessage={}", e.message, e)
            throw e
        }

    }


    /**
     * 发送群聊消息
     * @param robotCode 机器人编码
     * @param conversationId 开放群会话 ID
     */
    private fun sendGroupMessage(robotCode: String, conversationId: String,content: String): JSONObject {
        val client = createDingTalkClient()
        val headers = OrgGroupSendHeaders()
        headers.setXAcsDingtalkAccessToken(dingtalkUtils.getToken()) // 假设 dingtalkUtils.getToken() 可用

        val request = OrgGroupSendRequest()
        request.setMsgKey("sampleText") // 假设这是消息模板的 Key
        request.setRobotCode(robotCode)
        request.setOpenConversationId(conversationId)
        request.setMsgParam(content)

        val groupSendResponse = client.orgGroupSendWithOptions(request, headers, RuntimeOptions())

        if (java.util.Objects.isNull(groupSendResponse) || java.util.Objects.isNull(groupSendResponse.body)) {
            logger.error("发送群聊消息失败, response={}", groupSendResponse)
        }
        return JSONObject()
    }

    /**
     * 发送单聊消息
     * @param robotCode 机器人编码
     * @param userId 接收者用户 ID
     */
    private fun sendSingleMessage(robotCode: String, userId: String,content: String): JSONObject {
        val client = createDingTalkClient()
        val headers = BatchSendOTOHeaders()
        headers.setXAcsDingtalkAccessToken(dingtalkUtils.getToken())

        val request = BatchSendOTORequest()
        request.setMsgKey("sampleText")
        request.setRobotCode(robotCode)
        request.setUserIds(listOf(userId))
        request.setMsgParam( content)

        val userSendResponse = client.batchSendOTOWithOptions(request, headers, RuntimeOptions())

        if (java.util.Objects.isNull(userSendResponse) || java.util.Objects.isNull(userSendResponse.body)) {
            logger.error("发送单聊消息失败, response={}", userSendResponse)
        }
        return JSONObject()
    }

    /**
     * 创建钉钉 API 客户端
     */
    private fun createDingTalkClient(): com.aliyun.dingtalkrobot_1_0.Client {
        val config = Config()
        config.protocol = "https"
        config.regionId = "central"
        return com.aliyun.dingtalkrobot_1_0.Client(config)
    }

}