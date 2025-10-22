package rookie.servicedingbot.model.form

data class SampleChatForm(
    val model: String,
    val content: String,
    var conversionId: String?,
    var isGroupChat: Boolean?
)
