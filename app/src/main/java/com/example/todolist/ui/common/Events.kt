package com.example.todolist.ui.common

import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.delay

/** One-shot event holder so navigation/toast events fire exactly once. */
open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set

    fun getContentIfNotHandled(): T? =
        if (hasBeenHandled) null else {
            hasBeenHandled = true
            content
        }
}

/** Wraps a background block with a loading flag, ensuring a minimum display time. */
suspend fun <T> MutableLiveData<Boolean>.withMinLoading(minMs: Long = 400L, block: suspend () -> T): T {
    setValue(true)
    val start = System.currentTimeMillis()
    return try {
        block()
    } finally {
        val remaining = minMs - (System.currentTimeMillis() - start)
        if (remaining > 0) delay(remaining)
        setValue(false)
    }
}
