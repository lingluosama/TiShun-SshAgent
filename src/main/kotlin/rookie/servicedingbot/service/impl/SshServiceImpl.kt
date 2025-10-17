package rookie.servicedingbot.service.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.FingerprintVerifier
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import okio.Timeout
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import rookie.servicedingbot.config.SshConfig
import rookie.servicedingbot.model.consts.BusinessException
import rookie.servicedingbot.service.SshService
import java.security.PublicKey
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
class SshServiceImpl(
    private val connectionDetails: SshConfig.SshConnectionDetails
) : SshService, CoroutineScope by CoroutineScope(Dispatchers.IO) {

    companion object{
      val logger = LoggerFactory.getLogger(SshServiceImpl::class.java)
    }


    private val cachedClient= CompletableFuture.supplyAsync {
        try {
            return@supplyAsync this.connect()
        }catch (e: Exception){
            throw RuntimeException("Initial SSH connection failed", e)
        }
    }

    private fun connect(): SSHClient {
        val client = SSHClient()

        val hardcodedFingerprint = "SHA256:uy2yJ8MTgPR9oreTxE6XjHE5rJKnzPoCZ8ths0oQXMg"

        // FingerprintVerifier 接受一个指纹字符串列表
        client.addHostKeyVerifier(hardcodedFingerprint)
        client.connect(connectionDetails.host, connectionDetails.port)
        client.authPassword("root","439900")

        return client
    }

    override suspend fun getClient(): SSHClient {
        val client = cachedClient.get()

        //先从缓存拿连接
        if (!client.isConnected || !client.isAuthenticated) {
            println("SSH Client disconnected, attempting to reconnect...")
            try {
                // 确保旧连接被清理
                client.disconnect()
            } catch (ignore: Exception) { }

            // 重新创建并替换缓存
            val newClient = this.connect()
            cachedClient.complete(newClient)
            return withContext(Dispatchers.IO){
              newClient
            }
        }
        return withContext(Dispatchers.IO){
             client
        }
    }

    override suspend fun executeCommand(command: String): Flux<String> {
        val client = getClient()
        return Flux.create { sink ->
            this.launch {
                try {
                    val session = client.startSession()
                    val cmd = session.exec(command)

                    //将命令的输出流读取并转发给sink
                    cmd.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            sink.next(line)
                        }
                    }

                    cmd.join(10, TimeUnit.SECONDS)
                    val exitStatus = cmd.exitStatus

                    if(exitStatus!=0){
                        val errorOutPut = cmd.errorStream.bufferedReader().readText()
                        sink.error(BusinessException( "命令执行失败"))

                    }else{
                        sink.complete()
                    }

                    session.close()

                }catch (e: Exception){
                    sink.error(e)
                }finally {
                    client.disconnect()
                }
            }
        }
    }
}