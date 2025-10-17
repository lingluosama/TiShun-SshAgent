package rookie.servicedingbot.controller

import org.springframework.http.ResponseCookie
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import rookie.servicedingbot.model.form.SampleChatForm
import rookie.servicedingbot.service.ChatAgentService
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/agent")
class AgentChatController(
    private val chatAgentService: ChatAgentService,
) {


    @PostMapping("/sampleChat", produces = ["text/event-stream"])
    suspend fun simpleChat(
        @RequestBody form: SampleChatForm,
        exchange: ServerWebExchange
    ): Flux<String> {
        val response = exchange.response

        if(form.conversionId==null){
            val newId = UUID.randomUUID().toString()

            val cookie = ResponseCookie.from("conversationId", newId)
                .maxAge(Duration.ofDays(7))
                .httpOnly(true)
                .path("/")
                .build()
            response.addCookie(cookie)
            form.conversionId=newId
        }

        return chatAgentService.sampleChat(form).map { it+"\n" }
    }


}