package rookie.servicedingbot.function

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

interface Tool {
    fun getDefine():LlmFunction

    suspend fun execute(args:Map<String,Any>):Any

}

@Component
class ToolManager(private val applicationContext: ApplicationContext) : InitializingBean {

    companion object{
        private val logger = LoggerFactory.getLogger(ToolManager::class.java)
    }

    private val toolInstances: ConcurrentHashMap<String, Tool> = ConcurrentHashMap()

    private val toolDefinitions: ConcurrentHashMap<String, LlmFunction> = ConcurrentHashMap()


    fun getToolsInfo(): Map<String, LlmFunction> {
        return toolDefinitions
    }

    fun getToolByName(name: String): Tool? {
        return toolInstances[name]
    }


    override fun afterPropertiesSet() {
        val toolBeans = applicationContext.getBeansOfType(Tool::class.java)

        var count = 0
        toolBeans.forEach { (_, tool) ->
            try {
                val definition = tool.getDefine()
                val name = definition.name

                if (toolDefinitions.containsKey(name)) {
                    logger.error("Tool 重复注册, name:{}", name)
                    throw IllegalStateException("Tool 重复注册, name:${name}")
                }

                //存LlmFunction(给 LLM 用)
                toolDefinitions[name] = definition

                //存Tool实例(给自己执行用)
                toolInstances[name] = tool

                count++

            } catch (e: Exception) {
                logger.error("Tool 初始化失败, class:{}, error:{}", tool::class.simpleName, e.message)
            }
        }
        logger.info("ToolManager 注册了 {} 个工具。", count)
    }
}