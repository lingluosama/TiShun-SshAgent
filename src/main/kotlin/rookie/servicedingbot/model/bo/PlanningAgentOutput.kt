package rookie.servicedingbot.model.bo

/**
 *
 */
data class PlanningAgentOutput(
    //用户事件
    val event:String,

    //需求分析
    val demand:String,

    //解决方案
    val executionChain:List<String>,

    val nextAgent: String
)
