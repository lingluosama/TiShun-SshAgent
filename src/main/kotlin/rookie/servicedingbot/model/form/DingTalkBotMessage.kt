package rookie.servicedingbot.model.form


data class DingTalkMessage(
    val senderPlatform: String,
    val conversationId: String,
    val chatbotCorpId: String,
    val chatbotUserId: String,
    val openThreadId: String,
    val msgId: String,
    val senderNick: String,
    val isAdmin: Boolean,
    val senderStaffId: String?,
    val sessionWebhookExpiredTime: Long,
    val createAt: Long,
    val senderCorpId: String,
    val conversationType: String,
    val senderId: String,
    val sessionWebhook: String,
    val text: TextMessage,
    val robotCode: String,
    val msgtype: String
)

data class TextMessage(
    val content: String
)