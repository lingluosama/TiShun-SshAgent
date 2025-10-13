package rookie.servicedingbot

import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@MapperScan("rookie.servicedingbot.mapper")
class ServiceDingBotApplication

fun main(args: Array<String>) {
    runApplication<ServiceDingBotApplication>(*args)
}
