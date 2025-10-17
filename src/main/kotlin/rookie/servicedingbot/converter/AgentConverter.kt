package rookie.servicedingbot.converter

import org.springframework.stereotype.Component
import rookie.servicedingbot.model.bo.EvaluateAgentInput
import rookie.servicedingbot.model.bo.EvaluateAgentOutput
import rookie.servicedingbot.model.bo.PlanningAgentOutput
import rookie.servicedingbot.model.bo.ReplyAgentInput
import rookie.servicedingbot.model.bo.SshAgentInput
import rookie.servicedingbot.model.bo.SshAgentOutput


@Component
class AgentConverter {

    /**
     * 将 PlanningAgentOutput 转换为 EvaluateAgentInput。
     * 映射规则:
     * - event, executionChain, demand 直接映射。
     * - previousExecution, previousResult, suggestionFromSShAgent, processSummary 初始为空字符串/空列表。
     */
    fun planOutputToEvaluateInput(output: PlanningAgentOutput): EvaluateAgentInput {
        return EvaluateAgentInput(
            event = output.event,
            executionChain = output.executionChain,
            previousExecution = "",
            previousResult = "",
            suggestionFromSShAgent = "",
            processSummary = "",
            demand = output.demand,
            originalMessage = ""
        )
    }



    fun planOutputToReplyInput(output: PlanningAgentOutput): ReplyAgentInput {
        return ReplyAgentInput(
            originalMessage = "",
            executionChain = output.executionChain,
            processDescription = "",
            isSampleChat = true
        )
    }



    fun evaluateOutputToSshInput(output: EvaluateAgentOutput): SshAgentInput {
        return SshAgentInput(
            demand = output.demand,
            command = output.nextCommand,
            originalDemand = null,
            commandExecutionResult = ""
        )
    }



    fun sshOutputToEvaluateInput(output: SshAgentOutput): EvaluateAgentInput {
        return EvaluateAgentInput(
            event = "",
            executionChain = emptyList(),
            previousExecution = output.receivedCommand,
            previousResult = output.result,
            suggestionFromSShAgent = output.suggestion,
            processSummary = "",
            demand = "",
            originalMessage = ""
        )
    }


    fun evaluateOutputToReplyInput(output: EvaluateAgentOutput): ReplyAgentInput {
        return ReplyAgentInput(
            originalMessage = "",
            executionChain = output.executionChain,
            processDescription = output.processDescription,
            isSampleChat = false
        )
    }
}
