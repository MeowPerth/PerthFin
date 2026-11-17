package dev.jdtech.jellyfin.cast.dlna

class DlnaException(
    message: String,
    val errorCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
