package rookie.servicedingbot.function.test

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import rookie.servicedingbot.function.FunctionArgs
import rookie.servicedingbot.function.LlmFunction
import rookie.servicedingbot.function.Property
import rookie.servicedingbot.function.Tool

@Component
class testTool: Tool {

    companion object{
        private val functionArgs:FunctionArgs = FunctionArgs(
            type = "object",
            properties = mapOf(
                "args1" to Property(
                    type = "string",
                    description = "测试参数1"
                ),
                "args2" to Property(
                    type = "int",
                    description = "测试参数2"
                )
            ),
            required = listOf("args1")
        )
        val logger= LoggerFactory.getLogger(testTool::class.java)
    }
    override fun getDefine(): LlmFunction {
        return LlmFunction(
            name = "testTool",
            description = "测试工具",
            args = functionArgs
        )
    }

    override suspend fun execute(args: Map<String, Any>): Any {
        val args1 = args["args1"] as? String
        val args2 = (args["args2"] as? Number)?.toInt()

        if (args1.isNullOrBlank()) {
            logger.error("testTool call failed: args1 missing or empty.")
            return "TOOL_ERROR: Missing required argument 'args1'."
        }

        logger.info("testTool execution with args1: $args1, args2: $args2")
        return "Tool execution successful with output: ok"
    }
}

