package rookie.servicedingbot.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SshConfig {
    @Value("\${ssh.host}")
    private lateinit var host: String
    @Value("\${ssh.port}")
    private var port: Int=22

    @Bean
    fun sshConnectionDetails(): SshConnectionDetails {
        return SshConnectionDetails(host, port)
    }

    data class SshConnectionDetails(val host: String, val port: Int)
}