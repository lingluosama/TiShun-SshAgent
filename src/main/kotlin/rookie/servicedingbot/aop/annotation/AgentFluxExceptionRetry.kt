package rookie.servicedingbot.aop.annotation

import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AgentFluxExceptionRetry(
    val maxRetry: Int = 6,
    val retryInterval: Long = 5000L,
    val retryFor: KClass<out Exception>
)
