package rookie.servicedingbot.config

import com.google.genai.Client
import com.google.genai.types.HttpOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GeminiConfig {
    companion object{
        val logger = LoggerFactory.getLogger(GeminiConfig::class.java)
    }


    @Value("\${gemini.api.key:}")
    private val apiKey: String = ""

    @Value("\${gemini.api.endpoint:}")
    private val endpoint: String = ""

    @Bean
    fun googleGenAIClient(): Client {
        val httpOptions = HttpOptions.builder()
            .baseUrl(endpoint)
            .timeout(600)
            .build()
        return Client.Builder()
            .apiKey(apiKey)
            .httpOptions(httpOptions)
            .build()
    }

}