package rookie.servicedingbot.llmClient.clientImpl

import io.github.pigmesh.ai.deepseek.core.DeepSeekClient
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionRequest
import io.github.pigmesh.ai.deepseek.core.chat.ChatCompletionResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.util.retry.Retry
import rookie.servicedingbot.llmClient.LLMClient
import rookie.servicedingbot.model.consts.BusinessException
import rookie.servicedingbot.model.consts.SystemAgentCharacters
import java.time.Duration

@Component
class Deepseek(
    val deepseekClient: DeepSeekClient
): LLMClient {

    companion object{
        val logger = LoggerFactory.getLogger(Deepseek::class.java)
    }
    override fun chatFluxCompletion(model: String, prompt: String,characters: SystemAgentCharacters): Flux<String> {

        val request = ChatCompletionRequest.builder()
            .model(model)
            .temperature(0.5)
            .addSystemMessage(characters.agentInstructionText)//设定插入
            .addUserMessage(prompt)
            .build()
        val chatFluxCompletion: Flux<ChatCompletionResponse> = deepseekClient.chatFluxCompletion(request)

        //获取choices.message.content
        return chatFluxCompletion.flatMap { response ->
            response.choices()?.let {choices ->
                Flux.fromIterable(choices)
            }?: Flux.empty()
        }.map { choice ->
            val contentFormDelta=choice.delta()?.content()
            if(!contentFormDelta.isNullOrEmpty()){
                contentFormDelta
            }else{
                choice.message()?.content()?:""
            }
        }.retryWhen(
            Retry.backoff(3L,
                Duration.ofMillis(5000L)).doBeforeRetry {
                    logger.warn("【Deepseek接口异常】:${it.failure()},进行第${it.totalRetries()}次重试")
            })

    }

    override fun chatCompletion(
        model: String,
        prompt: String,
        characters: SystemAgentCharacters
    ): String {
        val request = ChatCompletionRequest.builder()
            .model(model)
            .temperature(0.5)
            .addUserMessage(characters.agentInstructionText)//设定插入
            .addUserMessage(prompt)
            .build()
        var retry=1;
        var execute: ChatCompletionResponse
        while (retry<=3){
            try {
                execute = deepseekClient.chatCompletion(request).execute()
                execute.choices()?.let {choices ->
                    return choices.get(0).message()?.content()?:"System:the agent reply is null"
                }
            }catch (e:Exception){
                logger.warn("【Deepseek接口异常】:${e.message},进行第${retry}次重试")
                retry++
            }
            retry ++
        }
        throw BusinessException("【DeepSeek接口异常】:重试超过最大次数")

    }
}