package rookie.servicedingbot.function

data class LlmFunction(
    val name:String,
    val description:String,
    val args:FunctionArgs
)

data class FunctionArgs(
    //类型名称
    val type:String="object",
    val properties:Map<String, Property>,
    val required:List<String>
)

data class Property(
    val type:String,
    val description:String
)
