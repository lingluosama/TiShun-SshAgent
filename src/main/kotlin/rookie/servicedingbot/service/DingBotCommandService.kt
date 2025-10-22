package rookie.servicedingbot.service

import org.aspectj.bridge.Message
import rookie.servicedingbot.model.form.DingTalkMessage
import shade.com.alibaba.fastjson2.JSONObject

interface DingBotCommandService {

    fun chatWithAgent(message: DingTalkMessage):JSONObject

    fun changeModel(message: DingTalkMessage):JSONObject

    fun getHelpDoc(message: DingTalkMessage): JSONObject

    fun cleanChatHistory(message: DingTalkMessage):JSONObject
}