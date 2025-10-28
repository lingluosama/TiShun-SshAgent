package rookie.servicedingbot.model.bo

//监控Agent的调用应该在评估Agent输出之后
data class MonitorAgentInput(
    //用户原始消息
    var originalUserMessage: String,

    var model: String,

    // Agent 迄今为止收集到的关键情报和洞察
    var collectedInsights: MutableCollection<String> = mutableListOf(),

    //迄今为止的主要执行步骤和结果摘要
    var processSummary: String,

    //需求分析
    var currentDemand:String,
) {
}