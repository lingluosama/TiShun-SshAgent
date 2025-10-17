package rookie.servicedingbot.service.impl

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import rookie.servicedingbot.converter.AgentConverter
import rookie.servicedingbot.model.bo.EvaluateAgentInput
import rookie.servicedingbot.model.bo.EvaluateAgentOutput
import rookie.servicedingbot.model.bo.PlanningAgentInput
import rookie.servicedingbot.model.bo.PlanningAgentOutput
import rookie.servicedingbot.model.bo.ReplyAgentInput
import rookie.servicedingbot.model.bo.SshAgentInput
import rookie.servicedingbot.model.bo.SshAgentOutput
import rookie.servicedingbot.model.consts.BusinessException
import rookie.servicedingbot.model.consts.SystemAgentCharacters
import rookie.servicedingbot.model.form.SampleChatForm
import rookie.servicedingbot.service.ChatAgentService
import rookie.servicedingbot.service.HistoryService
import rookie.servicedingbot.service.SshService
import shade.com.alibaba.fastjson2.JSON

@Service
class ChatAgentServiceImpl(
    val geminiClient: Client,
    val agentConverter: AgentConverter,
    val sshService: SshService,
    @Lazy private val self: ChatAgentService,
    val historyService: HistoryService
): ChatAgentService, CoroutineScope by CoroutineScope(Dispatchers.IO) {

    companion object{
        val logger = LoggerFactory.getLogger(ChatAgentServiceImpl::class.java)

        //移除模型输出中的 Markdown 代码块标记
        private fun cleanModelOutputJson(text: String): String {
            // (?s) 启用 DOTALL 模式，让 . 匹配包括换行符在内的所有字符
            // 匹配并提取 ```json ... ``` 或 ``` ... ``` 中的内容
            val regex = "(?s)```(json)?\\s*(.*?)\\s*```".toRegex()
            val match = regex.find(text.trim())

            // 如果匹配到代码块，则返回代码块内的内容，否则返回原始文本
            return match?.groupValues?.get(2)?.trim() ?: text.trim()
        }
    }

    override suspend fun sampleChat(form: SampleChatForm): Flux<String> {
        if(form.conversionId==null){
            throw BusinessException("conversionId 不能为空")
        }
        val historyMessage = historyService.getRecentHistoryByMemoryLimit(form.conversionId!!, 100)


        val input = PlanningAgentInput(form.content, form.model,historyMessage)
        return this.startTaskFlow(input) // 外部调用，不需要改动
    }

    private suspend fun startTaskFlow(input: PlanningAgentInput): Flux<String> {
        val planningOutput = self.planningAgent(input)

        // 检查是否决定直接回复
        if(planningOutput.nextAgent == "replyAgent"){
            val replyInput = agentConverter.planOutputToReplyInput(planningOutput)
            replyInput.originalMessage = input.userInput
            return self.replyAgent(replyInput)
        }

        // 启动评估 Agent 递归链
        val evaluateInput = agentConverter.planOutputToEvaluateInput(planningOutput)
        return self.driveEvaluationChain(evaluateInput)
    }

    override suspend fun planningAgent(input: PlanningAgentInput): PlanningAgentOutput {
        val config = GenerateContentConfig.builder()
            .systemInstruction(SystemAgentCharacters.PLANNING_AGENT.agentInstruction)
            .build()
        try {
            val contentText = geminiClient.models.generateContent(input.model, input.userInput, config).text()
            logger.info("【规划 Agent】API 执行成功，原始返回:\n{}", contentText)

            // 使用清理函数处理模型输出
            val cleanJson = contentText?.let { cleanModelOutputJson(it) }
            return JSON.parseObject(cleanJson, PlanningAgentOutput::class.java)
        }catch (e: Exception){
            logger.error("【规划 Agent】API 执行失败，错误信息: {}", e.message)
            throw  e
        }

    }

    override suspend fun driveEvaluationChain(input: EvaluateAgentInput): Flux<String> {
        val inputString = JSON.toJSONString(input)
        val config = GenerateContentConfig.builder()
            .systemInstruction(SystemAgentCharacters.EVALUATE_AGENT.agentInstruction)
            .build()

        try {
            val contentText = geminiClient.models.generateContent("gemini-2.5-flash", inputString, config).text()
            logger.info("【评估 Agent】API 执行成功，原始返回:\n{}", contentText)

            // 使用清理函数处理模型输出
            val cleanJson = contentText?.let { cleanModelOutputJson(it) }
            val output = JSON.parseObject(cleanJson, EvaluateAgentOutput::class.java)

            // 检查流程是否结束
            if(output.isPlanToReply){
                val replyInput = agentConverter.evaluateOutputToReplyInput(output)
                replyInput.originalMessage = input.originalMessage // 确保原始消息上下文传递
                return self.replyAgent(replyInput)
            }

            // 流程继续：调用 SSH
            val sshInput = agentConverter.evaluateOutputToSshInput(output)
            sshInput.originalDemand = output.demand // 使用 EvaluateAgent 生成的需求

            val sshAgentOutput = self.sshHandlerAgent(sshInput)

            // 准备下一轮 Evaluate Agent 的输入
            val nextInput = agentConverter.sshOutputToEvaluateInput(sshAgentOutput)
            nextInput.originalMessage = input.originalMessage
            nextInput.demand = output.demand
            nextInput.event = input.event
            nextInput.executionChain = output.executionChain
            nextInput.processSummary = output.processDescription

            return self.driveEvaluationChain(nextInput)

        }catch (e: Exception){
            logger.error("【评估 Agent】API 执行失败，错误信息: {}", e.message)
            throw e
        }
    }

    override suspend fun evaluateAgent(input: EvaluateAgentInput): EvaluateAgentOutput {
        val inputString = JSON.toJSONString(input)
        val config = GenerateContentConfig.builder()
            .systemInstruction(SystemAgentCharacters.EVALUATE_AGENT.agentInstruction)
            .build()
        try {
            val contentText = geminiClient.models.generateContent("gemini-2.5-flash", inputString, config).text()
            logger.info("【评估 Agent】API 执行成功，原始返回:\n{}", contentText)

            // 使用清理函数处理模型输出
            val cleanJson = contentText?.let { cleanModelOutputJson(it) }
            val output = JSON.parseObject(cleanJson, EvaluateAgentOutput::class.java)

            if(output.nextAgent=="sshAgent"){
                val sshInput = agentConverter.evaluateOutputToSshInput(output)
                sshInput.originalDemand = input.demand

                val sshAgentOutput = self.sshHandlerAgent(sshInput)
                val nextInput = agentConverter.sshOutputToEvaluateInput(sshAgentOutput)
                nextInput.demand = output.demand
                nextInput.event=input.event
                nextInput.executionChain=output.executionChain
                nextInput.processSummary=output.processSummary
                nextInput.originalMessage = input.originalMessage


                self.evaluateAgent(nextInput)
            }else{
                val replyInput = agentConverter.evaluateOutputToReplyInput(output)
                replyInput.originalMessage = input.originalMessage
                self.replyAgent(replyInput)
            }
            return output;
        }catch (e: Exception){
            logger.error("【评估 Agent】API 执行失败，错误信息: {}", e.message)
            throw  e
        }
    }

    override suspend fun sshHandlerAgent(input: SshAgentInput): SshAgentOutput {
        var sshResult: String
        try {
            sshResult = withContext(Dispatchers.IO) {
                sshService.executeCommand(input.command)
                    .reduce("") { accumulator, item -> accumulator + "\n" + item }
                    .awaitSingle() // 使用 awaitSingle
                    ?: "the command output is null"
            }
            input.commandExecutionResult = sshResult
        }catch (e: Exception){
            sshResult="From System: the command execution failed,the error message is ${e.message}"
        }
        input.commandExecutionResult = sshResult

        val config = GenerateContentConfig.builder()
            .systemInstruction(SystemAgentCharacters.SSH_HANDLER_AGENT.agentInstruction)
            .build()
        try {
            val contentText = geminiClient.models.generateContent("gemini-2.5-flash", JSON.toJSONString(input), config).text()
            logger.info("【SSH 处理 Agent】API 执行成功，原始返回:\n{}", contentText)

            // 使用清理函数处理模型输出
            val cleanJson = contentText?.let { cleanModelOutputJson(it) }
            val output = JSON.parseObject(cleanJson, SshAgentOutput::class.java)
            return output
        }catch (e: Exception){
            logger.error("【SSH 处理 Agent】API 执行失败，错误信息: {}", e.message)
            throw  e
        }
    }

    override suspend fun replyAgent(input: ReplyAgentInput): Flux<String> {
        val config = GenerateContentConfig.builder()
            .systemInstruction(SystemAgentCharacters.REPLY_AGENT.agentInstruction)
            .build()

        logger.info("【回复 Agent】准备生成最终回复")

        val contentResponses = geminiClient.models.generateContentStream(
            "gemini-2.5-flash",
            JSON.toJSONString(input),
            config
        )

        val flow: Flow<String> = contentResponses
            .asFlow()
            .mapNotNull { it.text() }

        return flow.asFlux()
    }
}