package rookie.servicedingbot.service.impl

import com.google.genai.Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import rookie.servicedingbot.converter.AgentConverter
import rookie.servicedingbot.function.ToolManager
import rookie.servicedingbot.llmClient.LLMClient
import rookie.servicedingbot.model.bo.*
import rookie.servicedingbot.model.consts.BusinessException
import rookie.servicedingbot.model.consts.SystemAgentCharacters
import rookie.servicedingbot.model.form.SampleChatForm
import rookie.servicedingbot.service.ChatAgentService
import rookie.servicedingbot.service.HistoryService
import rookie.servicedingbot.service.SshService
import rookie.servicedingbot.utils.DingtalkUtils
import shade.com.alibaba.fastjson2.JSON
import java.time.Duration
import java.util.concurrent.TimeoutException

@Service
class ChatAgentServiceImpl(
    val geminiClient: Client,
    val agentConverter: AgentConverter,
    val sshService: SshService,
    val llmClient:Map<String, LLMClient>,
    @Lazy private val self: ChatAgentService,
    val historyService: HistoryService,
    val redissonClient: RedissonClient,
    val dingtalkUtils: DingtalkUtils,
    val toolManager: ToolManager
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
        //SSH相关参数
        private const val MAX_OUTPUT_CHARS: Int = 48 * 1024// 最大输出文本大小,如果超过就截断处理
        private const val HEAD_LENGTH: Int = 10 * 1024 // 对话历史截取开头的 10KB
        private const val TAIL_LENGTH: Int = 5 * 1024  // 对话历史截取结尾的 5KB
        private const val SSH_TIMEOUT_SECONDS: Long = 30 // SSH 命令最大执行时间 30 秒


    }

    override suspend fun sampleChat(form: SampleChatForm): Flux<String> {
        if(form.conversionId==null){
            throw BusinessException("conversionId 不能为空")
        }



        val historyMessage = historyService.getRecentHistoryByMemoryLimit(form.conversionId!!, 100)

        val input = PlanningAgentInput(form.content, form.model,historyMessage,toolManager.getToolsInfo())
        return this.startTaskFlow(input,form.conversionId,form.isGroupChat)
    }

    private suspend fun startTaskFlow(input: PlanningAgentInput,conversionKey: String?,isGroupChat: Boolean?): Flux<String> {
        input.tools=toolManager.getToolsInfo()
        val planningOutput = this.planningAgent(input)

        // 检查是否决定直接回复
        if(planningOutput.nextAgent == "replyAgent"){
            val replyInput = agentConverter.planOutputToReplyInput(planningOutput)
            replyInput.originalMessage = input.userInput
            replyInput.model = input.model
            replyInput.historyMessage = input.historyMessage
            replyInput.isSampleChat=true
            return this.replyAgent(replyInput)
        }

        // 启动评估 Agent 递归链
        val evaluateInput = agentConverter.planOutputToEvaluateInput(planningOutput)
        evaluateInput.historyMessage=input.historyMessage
        evaluateInput.model=input.model
        evaluateInput.originalMessage=input.userInput
        return this.driveEvaluationChain(evaluateInput,1,conversionKey,isGroupChat)
    }

    override suspend fun planningAgent(input: PlanningAgentInput): PlanningAgentOutput {

        val client = getLLMClient(input.model)

        try {
            val contentText = client?.chatCompletion(input.model, input.userInput, SystemAgentCharacters.PLANNING_AGENT)
            logger.info("【规划 Agent】API 执行成功，原始返回:\n{}", contentText)

            // 使用清理函数处理模型输出
            val cleanJson = contentText?.let { cleanModelOutputJson(it) }
            return JSON.parseObject(cleanJson, PlanningAgentOutput::class.java)
        }catch (e: Exception){
            logger.error("【规划 Agent】API 执行失败，错误信息: {}", e.message)
            throw  e
        }

    }

    override suspend fun driveEvaluationChain(input: EvaluateAgentInput,deepth: Int,conversionId: String?,isGroupChat: Boolean?): Flux<String> {
        val inputString = JSON.toJSONString(input)
        val client = getLLMClient(input.model)

        try {
            val contentText = client.chatCompletion(input.model, inputString, SystemAgentCharacters.EVALUATE_AGENT)
            logger.info("【评估 Agent】API 执行成功，原始返回:\n{}", contentText)

            // 使用清理函数处理模型输出
            val cleanJson = contentText?.let { cleanModelOutputJson(it) }
            val output = JSON.parseObject(cleanJson, EvaluateAgentOutput::class.java)

            // 检查流程是否结束
            if(output.isPlanToReply||deepth>16){
                val replyInput = agentConverter.evaluateOutputToReplyInput(output)
                replyInput.originalMessage = input.originalMessage // 确保原始消息上下文传递
                replyInput.historyMessage = input.historyMessage
                replyInput.model=input.model
                replyInput.collectedInsights=input.collectedInsights
                if(deepth>16){
                    replyInput.processDescription+="System:Because of evaluationAgent drive recursion over than 17 times,the task has be terminated"
                }
                return this.replyAgent(replyInput)
            }

            var sshAgentOutput: SshAgentOutput= SshAgentOutput(
                "",
                true,
                result = "",
                suggestion = ""
            )
            if(output.nextAgent=="sshAgent"){
                val sshInput = agentConverter.evaluateOutputToSshInput(output)
                sshInput.originalDemand = output.demand // 使用 EvaluateAgent 生成的需求
                sshInput.model=input.model
                sshAgentOutput = self.sshHandlerAgent(sshInput)
            }
            val toolCallExecuteResult=mutableMapOf<String, String>()
            if(!output.toolCall.isEmpty()){
                output.toolCall.forEach { toolCall ->
                    val toolFunction = toolManager.getToolByName(toolCall.name)
                    if(toolFunction==null){
                        logger.error("【评估 Agent】无法找到工具函数: {}", toolCall.name)
                        toolCallExecuteResult[toolCall.name] = "无法找到工具函数: ${toolCall.name}"
                    }else{
                        try {
                            val toolResult = toolFunction.execute(toolCall.arguments)
                            toolCallExecuteResult[toolCall.name] = toolResult.toString()
                        } catch (e: Exception) {
                            logger.error("【评估 Agent】工具函数执行失败: {}", e.message)
                            toolCallExecuteResult[toolCall.name] = "工具函数执行失败: ${e.message}"
                        }
                    }
                }
            }

            if(!output.newInsight.isNullOrBlank()){
                input.collectedInsights.add(output.newInsight!!)
            }

            // 准备下一轮 Evaluate Agent 的输入
            val nextInput = agentConverter.sshOutputToEvaluateInput(sshAgentOutput)
            nextInput.originalMessage = input.originalMessage
            nextInput.demand = output.demand
            nextInput.event = input.event
            nextInput.collectedInsights = input.collectedInsights
            nextInput.executionChain = output.executionChain
            nextInput.processSummary = output.processDescription
            nextInput.historyMessage = input.historyMessage
            nextInput.model=input.model
            nextInput.preToolCallResult = toolCallExecuteResult



            // 每4次反馈一次结果
            if (deepth % 4 == 0 && deepth > 0&&conversionId!=null&&isGroupChat!=null) {
                logger.info("【评估 Agent】流程深度 {}, 执行中途进度汇报...", deepth)

                val replyInput = agentConverter.evaluateOutputToReplyInput(output)
                replyInput.originalMessage = input.originalMessage
                replyInput.historyMessage = input.historyMessage
                replyInput.model = input.model
                replyInput.isHalfway=true
                replyInput.collectedInsights=input.collectedInsights

                val feedbackFlux: Flux<String> = replyAgent(replyInput)

                val feedbackResult: String = feedbackFlux
                    .reduce("") { acc, item -> acc + item }
                    .awaitSingle() // 挂起协程，等待 Flux 完成并返回最终的聚合结果


                //调用replyAgent总结目前操作
                if(isGroupChat){
                    dingtalkUtils.fastMessageSendWithCallBackUrl(feedbackResult)
                }else{
                    dingtalkUtils.fastMessageSendToUser(feedbackResult,conversionId)
                }

            }



            return this.driveEvaluationChain(nextInput, deepth + 1,conversionId,isGroupChat)

        }catch (e: Exception){
            logger.error("【评估 Agent】API 执行失败，错误信息: {}", e.message)
            throw e
        }
    }

    override suspend fun evaluateAgent(input: EvaluateAgentInput): EvaluateAgentOutput {
        val inputString = JSON.toJSONString(input)
        val client = getLLMClient(input.model)
        try {
            val contentText = client.chatCompletion(input.model, inputString, SystemAgentCharacters.EVALUATE_AGENT)
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
                nextInput.model=input.model
                nextInput.executionChain=output.executionChain
                nextInput.processSummary=output.processSummary
                nextInput.originalMessage = input.originalMessage


                self.evaluateAgent(nextInput)
            }else{
                val replyInput = agentConverter.evaluateOutputToReplyInput(output)
                replyInput.originalMessage = input.originalMessage
                replyInput.historyMessage = input.historyMessage
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
                    .timeout(Duration.ofSeconds(SSH_TIMEOUT_SECONDS))
                    .reduce("") { accumulator, item -> accumulator + "\n" + item }
                    .awaitSingle() // 使用 awaitSingle
                    ?: "the command output is null"
            }
            //限制ssh输出长度
            if(sshResult.length>MAX_OUTPUT_CHARS){
                val totalLength=sshResult.length
                val midpart = totalLength - HEAD_LENGTH - TAIL_LENGTH

                if(midpart>0){
                    val head = sshResult.substring(0, HEAD_LENGTH)
                    val tail = sshResult.substring(totalLength - TAIL_LENGTH)

                    sshResult = """
                    [-- SSH OUTPUT TRUNCATED --]
                    [-- COMMAND: ${input.command} --]
                    [-- ORIGINAL LENGTH: $totalLength chars, OMITTED: $midpart chars --]
                    
                    $head
                    
                    ... [TRUNCATED MIDDLE CONTENT] ...
                    
                    $tail
                """.trimIndent()
                    logger.info("【SSH 处理 Agent】命令${input.command}输出长度超出限制，已经截断处理")
                }

            }

            input.commandExecutionResult = sshResult
        }catch (e : TimeoutException){
            sshResult="From System: the command execution timed out,limit time is $SSH_TIMEOUT_SECONDS seconds,the error message is ${e.message}"
            logger.error("【SSH处理 Agent】命令 [{}] 执行超时：{}", input.command, e.message)
        } catch (e: Exception){
            sshResult="From System: the command execution failed,the error message is ${e.message}"
            logger.error("【SSH处理 Agent】命令 [{}] 执行失败：{}", input.command, e.message)
        }
        input.commandExecutionResult = sshResult
        val client = getLLMClient(input.model)

        try {
            val contentText = client.chatCompletion(input.model, JSON.toJSONString(input), SystemAgentCharacters.SSH_HANDLER_AGENT)
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
        val client = getLLMClient(input.model)
        val chatFluxCompletion =
            client.chatFluxCompletion(input.model, JSON.toJSONString(input), SystemAgentCharacters.REPLY_AGENT)
        return chatFluxCompletion.doOnError {
            logger.error("【回复 Agent】API 执行失败，错误信息: {}", it.message)
        }.doOnComplete {
            logger.info("【回复 Agent】API 执行成功")
        }
    }

    private fun getLLMClient(model: String?): LLMClient {
        if (model == null || model.isEmpty()) {
            throw BusinessException("未指定模型")
        }
        return when {
            model.startsWith("gemini") -> llmClient["gemini"]
            model.startsWith("deepseek") -> llmClient["deepseek"]
            else -> llmClient["gemini"]
        } ?: throw BusinessException("未找到指定模型")
    }


}