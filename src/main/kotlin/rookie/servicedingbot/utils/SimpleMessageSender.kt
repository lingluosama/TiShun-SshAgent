package rookie.servicedingbot.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.RequestEntity.put
import org.springframework.stereotype.Component

@Component
class SimpleMessageSender(
    val okHttpClient: OkHttpClient,
    val jsonSerializer: Json
) {
    companion object{
        val logger: Logger? = LoggerFactory.getLogger(SimpleMessageSender::class.java)
    }

    @Value("\${dingtalk.simpleCallback}")
    private val simpleCallback: String=""

    suspend fun send(message: String): Boolean {
        val jsonObject : JsonObject= buildJsonObject {
            put("msgtype", "text")
            putJsonObject("text") {
                put("content", "[Log]$message")
            }
        }
        val jsonString = jsonSerializer.encodeToString(JsonObject.serializer(), jsonObject)


        val request = Request.Builder()
            .url(simpleCallback)
            .header("Content-Type", "application/json")
            .header("User-Agent", "Go-Script/1.0")
            .post(jsonString.toRequestBody())
            .build()

        okHttpClient.newCall(request).execute().use{ resp->
            if(!resp.isSuccessful){
                logger?.error("send message failed: ${resp.code} ${resp.message}")
                return false
            }
            return true
        }

    }

}