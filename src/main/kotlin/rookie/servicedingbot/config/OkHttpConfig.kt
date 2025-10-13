package rookie.servicedingbot.config

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.java.net.cookiejar.JavaNetCookieJar
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

@Configuration
class OkHttpConfig {

    /**
     * cookie自动保存
     */
    @Bean
    fun cookieJar(): CookieJar{
        var cookieManager = CookieManager()
        cookieManager.setCookiePolicy ( CookiePolicy.ACCEPT_ALL )

        return JavaNetCookieJar(cookieManager)
    }

    @Bean
    fun okHttpClient(cookieJar: CookieJar): OkHttpClient{
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(10, TimeUnit.SECONDS) // 连接超时
            .readTimeout(15, TimeUnit.SECONDS)    // 读取超时
            .writeTimeout(15, TimeUnit.SECONDS)   // 写入超时

            // .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()
    }
}