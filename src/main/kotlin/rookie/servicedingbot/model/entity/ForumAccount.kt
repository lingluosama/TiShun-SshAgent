package rookie.servicedingbot.model.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table
import com.mybatisflex.core.keygen.KeyGenerators

@Table("forum_account")
data class ForumAccount(
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    var id: Long?,

    var username: String?,
    var password: String?,
    var disabled: Boolean = false,
)
