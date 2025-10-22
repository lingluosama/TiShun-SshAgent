package rookie.servicedingbot.llmClient.clientImpl

import com.google.genai.Client
import com.google.genai.errors.ServerException
import com.google.genai.types.GenerateContentConfig
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.reactor.asFlux
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.util.retry.Retry
import rookie.servicedingbot.llmClient.LLMClient
import rookie.servicedingbot.model.consts.BusinessException
import rookie.servicedingbot.model.consts.SystemAgentCharacters
import rookie.servicedingbot.service.impl.ChatAgentServiceImpl.Companion.logger
import java.time.Duration

@Component
class Gemini(
    val geminiClient: Client
): LLMClient {
    override fun chatFluxCompletion(
        model: String,
        prompt: String,
        characters: SystemAgentCharacters
    ): Flux<String> {
        val config = GenerateContentConfig.builder()
            .systemInstruction(characters.agentInstruction)
            .build()

        val contentResponse = geminiClient.models.generateContentStream(model, prompt, config)

        val flow = contentResponse.asFlow().mapNotNull { it.text() }
        return flow.asFlux()
            .retryWhen(
                Retry.backoff(
                    3L,
                    Duration.ofMillis(5000L)
                ).filter { exception ->
                    exception is ServerException
                }.doBeforeRetry { retrySignal ->
                    logger.warn("【Flux 重试】${characters.character} 第 ${retrySignal.totalRetries() + 1} 次重试...")
                }
            )
    }

    override fun chatCompletion(
        model: String,
        prompt: String,
        characters: SystemAgentCharacters
    ): String {
        var retry=1

        while(retry<=3){
            try {
                val config = GenerateContentConfig.builder()
                    .systemInstruction(characters.agentInstruction)
                    .build()

                val contentResponse = geminiClient.models.generateContent(model, prompt, config)
                return contentResponse.text()?:"System:the agent result is null"
            } catch (e: Exception) {
                logger.warn("【Gemini 重试】${characters.character} 第 ${retry} 次重试...")
                retry++
            }
        }

        throw BusinessException("【Gemini接口异常】:重试超过最大次数")


    }
}