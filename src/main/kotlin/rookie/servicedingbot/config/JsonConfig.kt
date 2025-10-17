package rookie.servicedingbot.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlinx.serialization.json.Json
@Configuration
class JsonConfig {

    @Bean
    fun standardJson(): Json {
        return Json {
            // 忽略 JSON 中存在但 Kotlin 数据类中没有的字段。
            // 生产环境强烈建议开启，避免反序列化失败。
            ignoreUnknownKeys = true

            // 允许非严格模式下的 JSON 解析，例如允许未加引号的字段名
            isLenient = true

            // 格式化toString输出
             prettyPrint = true

            // 默认值是否包含在序列化输出中。
            encodeDefaults = true
        }
    }
}