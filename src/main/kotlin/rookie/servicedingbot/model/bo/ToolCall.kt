package rookie.servicedingbot.model.bo


data class ToolCall(
    val name: String,
    val arguments:Map<String,Any>
)