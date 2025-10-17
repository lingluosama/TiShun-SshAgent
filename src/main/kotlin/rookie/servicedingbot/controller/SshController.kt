package rookie.servicedingbot.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import rookie.servicedingbot.service.SshService


@RestController
@RequestMapping("/ssh")
class SshController(
    private val sshService: SshService
) {

    @GetMapping("/execute", produces = ["text/event-stream"])
    suspend fun executeStream(
        @RequestParam command: String
    ): Flux<String> {
        return sshService.executeCommand(command).map { it+ "\n"}
    }
}