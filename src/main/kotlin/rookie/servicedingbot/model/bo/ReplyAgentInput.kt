package rookie.servicedingbot.model.bo

data class ReplyAgentInput(

    var model: String,
    //用户原始消息
    var originalMessage: String,

    //经历的执行链
    val executionChain: List<String>,
    //需求执行结果
    var processDescription: String,

    var isSampleChat: Boolean,

    //历史消息
    var historyMessage: List<String>,

    var isHalfway: Boolean,

    // Agent 迄今为止收集到的关键情报和洞察
    var collectedInsights: MutableCollection<String> = mutableListOf()

    )
