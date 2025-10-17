package rookie.servicedingbot.model.bo

data class EvaluateAgentOutput (

    //对于执行者的需求描述
    val demand:String,

    //执行链应对下发的agent不可见，只传递给执行者agent需要知道的指令
    val nextCommand: String,

    //如果此项为true,说明需求已经执行完成或者无法再继续,应该描述整个需求到执行结果
    val isPlanToReply: Boolean,

    //对应isPlanToReply为true时，交给总结agent的上下文描述
    val processDescription: String,

    //迄今为止的主要执行步骤和结果摘要
    val processSummary: String,

    //执行计划链(命令列表)
    var executionChain: List<String>,

    val nextAgent: String,

){

}