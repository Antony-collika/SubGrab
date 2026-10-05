package com.subgrab.app.data

import com.subgrab.app.domain.FailureType
import java.io.IOException
import java.net.SocketTimeoutException

object FailureClassifier {
    fun classify(httpStatus: Int? = null, exception: Throwable? = null): FailureType = when {
        httpStatus == 429 -> FailureType.HTTP_429
        httpStatus == 403 && isBotDetection(exception) -> FailureType.BOT_DETECTION
        httpStatus == 403 && exception is HttpFailure -> FailureType.ACCESS_DENIED
        httpStatus == 403 -> FailureType.HTTP_403
        httpStatus != null && httpStatus in 500..599 -> FailureType.SERVER_ERROR
        exception is StorageFailure -> FailureType.STORAGE_ERROR
        exception is SocketTimeoutException ||
            exception?.javaClass?.simpleName?.contains("Timeout", true) == true -> FailureType.TIMEOUT
        exception is IOException -> FailureType.CONNECTION_ERROR
        exception is org.json.JSONException -> FailureType.PARSE_ERROR
        else -> FailureType.UNKNOWN
    }

    private fun isBotDetection(exception: Throwable?): Boolean {
        val text = when (exception) {
            is HttpFailure -> exception.body
            else -> exception?.message.orEmpty()
        }
        return listOf(
            "bot",
            "not a bot",
            "automated",
            "unusual traffic",
            "captcha",
            "sign in to confirm"
        ).any { text.contains(it, ignoreCase = true) }
    }

    fun benign(type: FailureType): Boolean =
        type in setOf(
            FailureType.NO_SUBTITLE,
            FailureType.LANGUAGE_UNAVAILABLE,
            FailureType.VIDEO_UNAVAILABLE
        )
}
