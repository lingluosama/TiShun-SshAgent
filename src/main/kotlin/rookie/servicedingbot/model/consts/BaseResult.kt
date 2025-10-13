package rookie.servicedingbot.model.consts

data class BaseResult<T>(
    val code: Int = 0,
    val msg: String = "",
    val data: T? = null
){
    companion object{
        fun <T> success(data: T?): BaseResult<T> {
            return BaseResult(code = 200, msg = "success", data = data)
        }

        fun failed(msg: String): BaseResult<String> {
            return BaseResult(code = 400, msg = msg, data = null)
        }

    }

}
