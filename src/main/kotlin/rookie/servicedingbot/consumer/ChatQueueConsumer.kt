package rookie.servicedingbot.consumer

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.redisson.api.RSemaphore
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import rookie.servicedingbot.model.form.SampleChatForm
import rookie.servicedingbot.service.ChatAgentService
import rookie.servicedingbot.service.HistoryService
import rookie.servicedingbot.utils.DingtalkUtils
import shade.com.alibaba.fastjson2.JSONObject
import java.util.concurrent.TimeUnit

@Component
class ChatQueueConsumer(
    val redissonClient: RedissonClient,
    val chatAgentService: ChatAgentService,
    val dingtalkUtils: DingtalkUtils,
    val jsonSerializer: Json,
    val historyService: HistoryService
) {


    companion object{
        val logger= LoggerFactory.getLogger(ChatQueueConsumer::class.java)

        //聊天队列名称
        private val CHAT_QUEUE_NAME = "chatting:processing_queue"
        //聊天限流信号Key
        private val CHAT_SEMAPHORE_NAME = "chatting:global_limit"
        //最大并发聊天数
        private val MAX_CONCURRENT_CHATS = 5

        private const val CHAT_LOCK_WAITING_SECONDS: Long = 0
        //suspend方法没法用看门狗，有递归不想放到阻塞IO线程池里，设置长时间超时吧
        private const val CHAT_LOCK_TIMEOUT_SECONDS: Long = 900
        val chatIsBusy ="主人，我还在正在处理中，等在下一下吧QAQ"
    }

    @PostConstruct
    fun startConsumer() {
        val semaphore = redissonClient.getSemaphore(CHAT_SEMAPHORE_NAME)
        semaphore.trySetPermits(MAX_CONCURRENT_CHATS)

        //在Java中应该使用独立线程池实现
        GlobalScope.launch {
            val queue = redissonClient.getBlockingQueue<String>(CHAT_QUEUE_NAME)
            while(true){
                try {
                    val queueInputJson = withContext(Dispatchers.IO) {
                        queue.take()
                    }
                    launch {
                        processQueueElement(queueInputJson, semaphore)
                    }
                }catch (e : InterruptedException){
                    Thread.currentThread().interrupt()
                    logger.warn("队列消费协程被中断")
                    break
                }catch (e : Exception){
                    logger.error("处理聊天队列元素时发生未知错误", e)
                }

            }
        }
    }

    private suspend fun processQueueElement(queueInputJson: String,semaphore: RSemaphore) {


        val input = JSONObject.parseObject(queueInputJson)
        val chatForm = JSONObject.parseObject(input.getString("chatForm"), SampleChatForm::class.java)
        val conversationId = input.getString("conversationId")
        val robotCode = input.getString("robotCode")
        val userId = input.getString("userId")
        val isGroupChat = input.getBoolean("isGroupChat")

        semaphore.acquire() //排队等待许可

        val historyKey=if(chatForm.isGroupChat == true)conversationId else userId
        var reply=""
        val chatLock = redissonClient.getLock("chatting:$historyKey")
        val isGetLock = chatLock.tryLock(CHAT_LOCK_WAITING_SECONDS, CHAT_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        try {
            if(isGetLock){
                val flux = chatAgentService.sampleChat(chatForm)
                logger.warn("【消费者】开始处理队列元素，已获取信号量,内容: $chatForm")
                reply = flux.map { it.trim() }
                    .reduce("") { acc, item -> acc + item }
                    .awaitSingle()

                val historyJson = buildJsonObject {
                    put("userMessage",chatForm.content)
                    put("agentReply", reply)
                }
                val historyJsonString = jsonSerializer.encodeToString(JsonObject.serializer(), historyJson)
                if(!reply.isEmpty()){
                    historyService.addHistory(historyKey,historyJsonString)
                }
            }else{
                reply=chatIsBusy
            }
        }catch (e: Exception){
            reply= "任务处理失败: ${e.message}"
            logger.error("聊天Agent处理请求失败", e)
        }finally {
            if(chatForm.isGroupChat== true){
                dingtalkUtils.fastMessageSendWithCallBackUrl( reply)
            }else{
                dingtalkUtils.fastMessageSendToUser(reply,userId)
            }
            if(isGetLock){
                try {
                    if (chatLock.isHeldByCurrentThread) {
                        chatLock.unlock()
                    } else {
                        logger.warn("会话 $historyKey 锁释放失败！锁不在当前协程/线程持有")
                    }
                } catch (e: IllegalMonitorStateException) {
                    logger.warn("会话 $historyKey 可能已自动过期或被释放。", e)
                }
            }
            semaphore.release()
        }


    }

}