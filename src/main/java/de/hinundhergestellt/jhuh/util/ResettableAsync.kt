package de.hinundhergestellt.jhuh.util

import org.springframework.core.task.AsyncTaskExecutor
import java.util.Collections.synchronizedList
import java.util.concurrent.Future
import kotlin.reflect.KProperty

fun <T> asyncWithReset(executor: AsyncTaskExecutor, initializer: () -> T) = ResettableAsync(executor, initializer)

class ResettableAsync<T>(
    private val executor: AsyncTaskExecutor,
    private val initializer: () -> T
) {
    private var value by atomicNullable<T>()
    private var future by atomicNullable<Future<*>>()

    val stateChangeListeners: MutableList<() -> Unit> = synchronizedList(mutableListOf())

    init {
        refresh()
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        value?.let { return it } // if there's a stored value, return it, otherwise...
        future!!.get() // ...there's a future that will either throw or set the value, so...
        return value!! // ...there's a value when get finished without throwing
    }

    fun refresh(wait: Boolean = false) {
        val future = executor.submit {
            try {
                value = initializer()
                synchronized(stateChangeListeners) {
                    stateChangeListeners.forEach { it() }
                }
            } catch (e: Throwable) {
                value = null
                throw e
            }
        }
        this.future = future

        if (wait) {
            future.get()
        }
    }
}