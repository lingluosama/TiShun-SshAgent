package rookie.servicedingbot.service

import com.mybatisflex.core.service.IService
import okhttp3.OkHttpClient
import rookie.servicedingbot.model.entity.ForumAccount

interface ForumService: IService<ForumAccount> {

    suspend fun processSchedule(): Boolean

    suspend fun login(username:String,password:String): OkHttpClient

    suspend fun sign(client: OkHttpClient): Boolean

    suspend fun addAccount(account: ForumAccount): Boolean
}