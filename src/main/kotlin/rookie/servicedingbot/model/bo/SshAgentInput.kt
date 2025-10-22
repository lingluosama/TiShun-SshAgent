package rookie.servicedingbot.model.bo

data class SshAgentInput(

    var model: String,
    //需求分析
    var demand:String,
    //给出的命令,应在调用模型之前就手动调用ssh执行此项
    var command: String,
    //命令执行结果
    var commandExecutionResult: String,
    // PlanAgent 原始的需求分析
    var originalDemand: String?
)
