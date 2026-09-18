package com.subgrab.app.data

import android.util.Log

object DebugLog {
    private const val TAG = "SubGrabNet"
    private const val MAX_LINES = 800
    private val lock = Any()
    private val lines = ArrayDeque<String>()

    fun clear() {
        synchronized(lock) {
            lines.clear()
        }
    }

    fun snapshot(): List<String> {
        return synchronized(lock) {
            lines.toList()
        }
    }

    fun text(): String {
        return snapshot().joinToString("\n")
    }

    fun d(message: String) {
        synchronized(lock) {
            lines.addLast(message)
            while (lines.size > MAX_LINES) {
                lines.removeFirst()
            }
        }
        Log.d(TAG, message)
    }

    fun e(message: String, error: Throwable? = null) {
        d(
            "ERROR $message" +
                if (error == null) {
                    ""
                } else {
                    " | ${error.javaClass.simpleName}: ${error.message}"
                }
        )
        if (error != null) {
            Log.e(TAG, message, error)
        }
    }
}
