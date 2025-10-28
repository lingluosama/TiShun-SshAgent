package rookie.servicedingbot.model.bo

import rookie.servicedingbot.function.LlmFunction

data class EvaluateAgentInput(

    var model: String,
    //用户事件
    var event: String,

    //执行计划链(命令列表)
    var executionChain: List<String> =emptyList(),

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
    var originalUserMessage: String,

    //历史消息
    var historyMessage: List<String> =emptyList(),

    var depth: Int,

    //Agent 可用工具
    var tools: Map<String, LlmFunction> =emptyMap(),

    //上次工具调用结果
    var preToolCallResult: Map<String, String> =emptyMap(),

    // Agent 迄今为止收集到的关键情报和洞察
    var collectedInsights: MutableCollection<String> = mutableListOf(),

    //是否需要立即修正
    var needCorrect: Boolean=false
)
