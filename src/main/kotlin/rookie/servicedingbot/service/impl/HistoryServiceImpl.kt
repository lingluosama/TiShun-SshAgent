package rookie.servicedingbot.service.impl

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import rookie.servicedingbot.service.HistoryService
import java.nio.charset.StandardCharsets

@Service
class HistoryServiceImpl(
    val redisTemplate: RedisTemplate<String, String>
): HistoryService {
    override fun getRecentHistoryByMemoryLimit(
        key: String,
        kb: Long
    ): List<String> {
        val listOps = redisTemplate.opsForList()
        val totalLength=listOps.size(key)?:0
        if(totalLength==0L){
            return emptyList()
        }

        val res = mutableListOf<String>()
        var currentSize=0L
        var stopIndex=-1L

        while(stopIndex>=-totalLength){
            val messageList = listOps.range(key, stopIndex, stopIndex)

            val message = messageList?.firstOrNull()?:break

            val size = message.toByteArray(StandardCharsets.UTF_8).size

            if(currentSize+size>kb*1024){
                break
            }
            res.add(message)
            currentSize+=size

            stopIndex--
        }
        res.reverse()

        return res
    }
}