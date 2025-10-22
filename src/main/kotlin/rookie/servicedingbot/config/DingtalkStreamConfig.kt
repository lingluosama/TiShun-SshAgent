package rookie.servicedingbot.config

import com.dingtalk.open.app.api.OpenDingTalkClient
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder
import com.dingtalk.open.app.api.security.AuthClientCredential
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import rookie.servicedingbot.model.form.DingTalkMessage
import rookie.servicedingbot.controller.DingBotMessageHandler
import shade.com.alibaba.fastjson2.JSONObject

@Configuration
class DingtalkStreamConfig(
    val dingBotMessageConsumer: DingBotMessageHandler
) {
    @Value("\${dingtalk.app.SuiteKey}")
    lateinit var suiteKey: String

    @Value("\${dingtalk.app.SuiteSecret}")
    lateinit var suiteSecret: String

    @Value("\${dingtalk.app.topic}")
    var topic: String="/v1.0/im/bot/messages/get"

    companion object {
        val logger = LoggerFactory.getLogger(DingtalkStreamConfig::class.java)
    }

    @Bean(destroyMethod = "stop")
    fun dingtalkStreamClient(): OpenDingTalkClient {
        logger.info("钉钉 Stream Client 初始化中...")

        val client = OpenDingTalkStreamClientBuilder.custom()
            .credential(AuthClientCredential(suiteKey, suiteSecret))

            .registerCallbackListener<DingTalkMessage, JSONObject>(topic) { robotMessage ->
                logger.info("Received robotMessage on topic {}, content: {}", topic, robotMessage)
                return@registerCallbackListener dingBotMessageConsumer.execute(robotMessage)
            }
            .build()

        client.start()

        logger.info("钉钉 Stream Client 启动成功, 正在监听事件, topic: {}", topic)

        return client
    }


}
