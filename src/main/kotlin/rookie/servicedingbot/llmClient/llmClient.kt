package rookie.servicedingbot.llmClient

import reactor.core.publisher.Flux
import rookie.servicedingbot.model.consts.SystemAgentCharacters

interface LLMClient {
    fun chatFluxCompletion(model: String, prompt: String,characters: SystemAgentCharacters): Flux<String>

    fun chatCompletion(model: String, prompt: String,characters: SystemAgentCharacters): String
}