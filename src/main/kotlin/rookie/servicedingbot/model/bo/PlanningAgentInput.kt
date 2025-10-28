package rookie.servicedingbot.model.bo

import rookie.servicedingbot.function.LlmFunction

data class PlanningAgentInput(
    //用户输入
    val userInput: String,
    val model: String,
    val historyMessage: List<String>,

    //让planAgent也参进tool决策
    var tools: Map<String, LlmFunction>
)
