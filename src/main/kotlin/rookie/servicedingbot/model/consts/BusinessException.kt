package rookie.servicedingbot.model.consts

import java.lang.Exception
import java.lang.RuntimeException


class BusinessException(
    private var code: Int = 500,
    message: String? = null, // 父类 message 参数，默认 null
    cause: Throwable? = null // 父类 cause 参数，默认 null
) : RuntimeException(message, cause) {

    constructor(code: Int, message: String) : this(code, message, null)

    constructor(exception: Exception,message: String?=null) : this(500, message?:exception.message, exception)

    constructor(message: String): this(500, message)
}
