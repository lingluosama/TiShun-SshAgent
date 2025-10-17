package rookie.servicedingbot.model.bo

data class PlanningAgentInput(
    //用户输入
    val userInput: String,
    val model: String,
    val historyMessage: List<String>
)
