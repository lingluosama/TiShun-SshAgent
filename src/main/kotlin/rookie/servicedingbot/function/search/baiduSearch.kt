package rookie.servicedingbot.function.search

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import rookie.servicedingbot.function.FunctionArgs
import rookie.servicedingbot.function.LlmFunction
import rookie.servicedingbot.function.Property
import rookie.servicedingbot.function.Tool
import kotlinx.serialization.json.*
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value

@Component
class baiduSearch(
    val okHttpClient: OkHttpClient,
    val jsonSerializer: Json
): Tool {


    @Value("\${baidu.apiKey}")
    var apiKey: String=""

    companion object{
        private val functionArgs: FunctionArgs = FunctionArgs(
            type = "object",
            properties = mapOf(
                "content" to Property(
                    type = "string",
                    description = "搜索内容"
                ),
                "recency_filter" to Property(
                    type = "string",
                    description = "根据网页发布时间进行筛选。枚举值:\n" +
                            "week:最近7天\n" +
                            "month：最近30天\n" +
                            "semiyear：最近180天\n" +
                            "year：最近365天"
                )
            ),
            required = listOf("content")
        )
        val logger= LoggerFactory.getLogger(baiduSearch::class.java)
    }

    override fun getDefine(): LlmFunction {
        return LlmFunction(
            name = "baidu_search",
            description = "百度搜索,可用于进行时效性信息查询",
            args = functionArgs
        )
    }

    override suspend fun execute(args: Map<String, Any>): Any {
        val message = args["content"] as String
        val recency_filter = args["recency_filter"] as? String

        val jsonObject : JsonObject= buildJsonObject {
            if(!recency_filter.isNullOrBlank())put("search_recency_filter", recency_filter)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", message)
                    }
                )
            }
        }
        val request = Request.Builder()
            .url("https://qianfan.baidubce.com/v2/ai_search/web_search")
            .method(
                "POST", jsonSerializer.encodeToString(
                    JsonObject.serializer(),
                    jsonObject
                )
                    .toRequestBody()
            )
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", apiKey)
            .build()

        okHttpClient.newCall(request).execute().use {response ->
            if(!response.isSuccessful){
                logger.error("百度搜索调用失败: ${response.code}, ${response.message}")
                return "the tool execute is failed,message from client: ${response.message}"
            }
            return response.body?.string() ?: "the tool execute is failed,message from client: ${response.message}"
        }

    }
}