package rookie.servicedingbot.service

import com.google.genai.errors.ServerException
import reactor.core.publisher.Flux
import rookie.servicedingbot.aop.annotation.AgentExceptionRetry
import rookie.servicedingbot.aop.annotation.AgentFluxExceptionRetry
import rookie.servicedingbot.model.bo.EvaluateAgentInput
import rookie.servicedingbot.model.bo.EvaluateAgentOutput
import rookie.servicedingbot.model.bo.PlanningAgentInput
import rookie.servicedingbot.model.bo.PlanningAgentOutput
import rookie.servicedingbot.model.bo.ReplyAgentInput
import rookie.servicedingbot.model.bo.SshAgentInput
import rookie.servicedingbot.model.bo.SshAgentOutput
import rookie.servicedingbot.model.form.SampleChatForm

interface ChatAgentService {


    suspend fun sampleChat(form: SampleChatForm): Flux<String>

    //分析聊天的输入，并决策下一步agent调用
    suspend fun planningAgent(input: PlanningAgentInput): PlanningAgentOutput

    //调用其他执行者agent，并不断自修正调用链
    suspend fun evaluateAgent(input: EvaluateAgentInput): EvaluateAgentOutput

    //执行命令并总结执行结果，交给评估agent
    suspend fun sshHandlerAgent(input: SshAgentInput): SshAgentOutput

    //总结任务结果，并根据设定发送消息给用户
    suspend fun replyAgent(input: ReplyAgentInput): Flux<String>

    //评估agent递归调用
    suspend fun driveEvaluationChain(input: EvaluateAgentInput,depth: Int,conversionId: String?,isGroupChat: Boolean?): Flux<String>

    suspend fun monitorAgent()
}