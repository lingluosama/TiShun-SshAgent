package rookie.servicedingbot.model.bo

data class SshAgentOutput(

    //接收到的命令
    val receivedCommand:String,

    //是否成功执行
    val isSuccess: Boolean,

    //执行结果总结
    val result: String,

    //建议
    val suggestion:String
)