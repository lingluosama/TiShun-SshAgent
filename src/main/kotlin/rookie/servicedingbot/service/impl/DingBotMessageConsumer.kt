package rookie.servicedingbot.service.impl

import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest
import com.aliyun.dingtalkrobot_1_0.models.BatchOTOQueryRequest
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTOHeaders
import com.aliyun.dingtalkrobot_1_0.models.BatchSendOTORequest
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendHeaders
import com.aliyun.dingtalkrobot_1_0.models.OrgGroupSendRequest
import com.aliyun.tea.TeaException
import com.aliyun.teaopenapi.models.Config
import com.aliyun.teautil.models.RuntimeOptions
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import rookie.servicedingbot.model.form.DingTalkMessage
import shade.com.alibaba.fastjson2.JSONObject

@Service
class DingBotMessageConsumer: OpenDingTalkCallbackListener<DingTalkMessage, JSONObject> {

    companion object {
        val logger = LoggerFactory.getLogger(DingBotMessageConsumer::class.java)
    }

    @Value("\${dingtalk.app.SuiteKey}")
    lateinit var suiteKey: String

    @Value("\${dingtalk.app.SuiteSecret}")
    lateinit var suiteSecret: String

    @Value("\${dingtalk.app.topic}")
    var topic: String="/v1.0/im/bot/messages/get"

    override fun execute(message: DingTalkMessage?): JSONObject? {
        message?.let {
            message -> when(message.conversationType){
                "1"->{
                   return replayUserMessage(message, "这是单聊测试")
                }
                "2"->{
                    return replyGroupMessage(message, "这是群聊测试")
                }
            }
        }
        return JSONObject()

    }


    fun replyGroupMessage(robotMessage: DingTalkMessage, replayContent: String): JSONObject{

        val userId = robotMessage.senderStaffId.toString()
        val message = robotMessage.text.content
        val conversationId = robotMessage.conversationId
        val robotCode = robotMessage.robotCode

        val sendHeaders = OrgGroupSendHeaders()
        sendHeaders.setXAcsDingtalkAccessToken(getToken())
        val request = OrgGroupSendRequest()
        request.setMsgKey("sampleText")
        request.setRobotCode(robotCode)
        request.setOpenConversationId(conversationId)
        val replay = JSONObject()
        replay["content"] = replayContent
        request.setMsgParam(replay.toJSONString())

        try {
            val config = Config()
            config.protocol = "https"
            config.regionId = "central"
            val client = com.aliyun.dingtalkrobot_1_0.Client(config)
            val groupSendResponse = client.orgGroupSendWithOptions(request, sendHeaders, RuntimeOptions())

            if(java.util.Objects.isNull(groupSendResponse)|| java.util.Objects.isNull(groupSendResponse.body)){
                logger.error("发送群聊消息失败, response={}"
                    ,groupSendResponse);
            }
            return JSONObject()
        }catch (e: TeaException){
            logger.error("机器人API交互错误, errCode={}, " +
                    "errorMessage={}", e.getCode(), e.message, e)
            throw e
        }catch (e: Exception){
            logger.error("机器人群聊发送业务异常, errorMessage={}", e.message, e)
            throw e
        }
    }

    fun replayUserMessage(robotMessage: DingTalkMessage, replayContent: String): JSONObject{
        val userId = robotMessage.senderStaffId.toString()
        val content = robotMessage.text.content
        val robotCode = robotMessage.robotCode

        val headers = BatchSendOTOHeaders()
        headers.setXAcsDingtalkAccessToken(getToken())

        val request = BatchSendOTORequest()
        request.setMsgKey("sampleText")
        request.setRobotCode(robotCode)
        request.setUserIds(listOf(userId))

        val replay = JSONObject()
        replay["content"] = replayContent
        request.setMsgParam(replay.toJSONString())

        try {
            val config = Config()
            config.protocol = "https"
            config.regionId = "central"
            val client = com.aliyun.dingtalkrobot_1_0.Client(config)
            val response = client.batchSendOTOWithOptions(request, headers, RuntimeOptions())
            if(java.util.Objects.isNull(response)|| java.util.Objects.isNull(response)){
                logger.error("发送单聊消息失败, response={}",response);
            }
            return JSONObject()
        }catch (e: TeaException){
            logger.error("机器人API交互错误, errCode={}, " +
                    "errorMessage={}", e.getCode(), e.message, e)
            throw e
        }catch (e: Exception){
            logger.error("机器人单聊发送业务异常, errorMessage={}", e.message, e)
            throw e
        }


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
            logger.error("获取钉钉 access_token 失败 ${e.message}", e)
            return ""
        }
    }

}