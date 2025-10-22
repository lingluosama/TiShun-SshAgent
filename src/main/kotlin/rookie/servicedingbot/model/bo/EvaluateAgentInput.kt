package rookie.servicedingbot.model.bo

data class EvaluateAgentInput(

    var model: String,
    //用户事件
    var event: String,

    //执行计划链(命令列表)
    var executionChain: List<String>,

    //上一次执行项
    val previousExecution: String,

    //上次执行总结
    val previousResult: String,

    val suggestionFromSShAgent: String,

    //迄今为止的主要执行步骤和结果摘要
    var processSummary: String,

    //需求分析
    var demand:String,

    //用户原始消息
    var originalMessage: String,

    //历史消息
    var historyMessage: List<String>,

    var depth: Int

    )
