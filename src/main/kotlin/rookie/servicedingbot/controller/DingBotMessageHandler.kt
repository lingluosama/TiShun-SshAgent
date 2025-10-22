package rookie.servicedingbot.controller

import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import rookie.servicedingbot.model.form.DingTalkMessage
import rookie.servicedingbot.service.DingBotCommandService
import shade.com.alibaba.fastjson2.JSONObject

@Service
class DingBotMessageHandler(
    val dingBotCommandService: DingBotCommandService
): OpenDingTalkCallbackListener<DingTalkMessage, JSONObject> {

    companion object {
        val logger = LoggerFactory.getLogger(DingBotMessageHandler::class.java)

    }

    override fun execute(message: DingTalkMessage?): JSONObject? {
        message ?: return JSONObject()

        val userContent = message.text.content.trim()

        return when {
            userContent.startsWith("/change", ignoreCase = true) -> {
                dingBotCommandService.changeModel(message)
            }

            userContent.equals("/clean", ignoreCase = true) -> {
                dingBotCommandService.cleanChatHistory(message)
            }

            userContent.equals("/help", ignoreCase = true) -> {
                dingBotCommandService.getHelpDoc(message)
            }

            else -> {
                dingBotCommandService.chatWithAgent(message)
            }
        }

    }


}