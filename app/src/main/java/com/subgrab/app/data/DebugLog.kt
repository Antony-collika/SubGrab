package com.subgrab.app.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DebugLog {
    private const val TAG = "SubGrabNet"
    private const val MAX_LINES = 800
    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private val _updates = MutableStateFlow(0L)
    val updates: StateFlow<Long> = _updates.asStateFlow()

    fun refresh() { _updates.value++ }

    fun clear() {
        synchronized(lock) {
            lines.clear()
            _updates.value++
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
            _updates.value++
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
