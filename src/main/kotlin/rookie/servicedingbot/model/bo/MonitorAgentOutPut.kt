package rookie.servicedingbot.model.bo

data class MonitorAgentOutPut(
    //是否应该提交回复
    val shouldReply: Boolean,

    //是否应该立即修正行为
    val needCorrect: Boolean,

    //修正后的需求
    val revisedRequirements: String

) {
}