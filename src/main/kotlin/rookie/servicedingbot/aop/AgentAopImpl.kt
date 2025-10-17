package rookie.servicedingbot.aop

import com.google.genai.errors.ServerException
import com.google.protobuf.ServiceException
import kotlinx.coroutines.delay
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.util.retry.Retry
import rookie.servicedingbot.aop.annotation.AgentExceptionRetry
import rookie.servicedingbot.aop.annotation.AgentFluxExceptionRetry
import java.time.Duration

@Component
class AgentAopImpl() {

    companion object{
        val logger = LoggerFactory.getLogger(AgentAopImpl::class.java)
    }

    @Around("@annotation(agentFluxExceptionRetry)")
    fun retryForFlux(joinPoint: ProceedingJoinPoint, agentFluxExceptionRetry: AgentFluxExceptionRetry): Any? {
        val result = joinPoint.proceed()

        if(result is Flux<*>) {
            val methodName = (joinPoint.signature as MethodSignature).name

            return result.retryWhen(
                Retry.backoff(
                    agentFluxExceptionRetry.maxRetry.toLong(),
                    Duration.ofMillis(agentFluxExceptionRetry.retryInterval)
                )
                    .doBeforeRetry { retrySignal ->
                        val currentAttempt = retrySignal.totalRetries() + 1
                        logger.warn(
                            "【Flux 重试】方法: $methodName，捕获到异常: ${retrySignal.failure()?.javaClass?.simpleName}。第 $currentAttempt 次重试..."
                        )
                    }
                    .filter { exception ->
                        agentFluxExceptionRetry.retryFor.isInstance(exception)
                    }
            )
        }
        return result
    }

    @Around("@annotation(agentExceptionRetry)")
    suspend fun retryForException(joinPoint: ProceedingJoinPoint, agentExceptionRetry: AgentExceptionRetry):Any{
        val methodName=(joinPoint.signature as MethodSignature).name
        var hasRetry=0
        var delayTime=agentExceptionRetry.retryInterval

        while(hasRetry<=agentExceptionRetry.maxRetry){
            hasRetry++
            try {
                return joinPoint.proceed()
            } catch (e: Exception) {
                if (agentExceptionRetry.retryFor.isInstance(e)) {
                    if (hasRetry <= agentExceptionRetry.maxRetry) {
                        logger.warn("【协程重试】方法: $methodName，捕获到 ${e::class.simpleName}。第 $hasRetry/${agentExceptionRetry.maxRetry + 1} 次尝试，等待 ${delayTime}ms 后重试...")
                        delay(delayTime)
                        delayTime *= 2
                    } else {
                        logger.error("【协程失败】方法: $methodName，已超过最大重试次数 (${agentExceptionRetry.maxRetry})。", e)
                        throw e
                    }
                } else {
                    logger.error("【协程失败】方法: $methodName，捕获到非目标异常。", e)
                    throw e
                }
            }
        }
        throw IllegalStateException("重试循环已跳出，但未发现异常?")

    }


}