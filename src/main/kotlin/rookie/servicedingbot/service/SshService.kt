package rookie.servicedingbot.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import reactor.core.publisher.Flux


interface SshService {
    suspend fun getClient(): SSHClient;
    suspend fun executeCommand(command: String): Flux<String>;
}