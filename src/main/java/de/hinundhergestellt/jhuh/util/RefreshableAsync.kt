package de.hinundhergestellt.jhuh.util

import org.springframework.core.task.AsyncTaskExecutor
import java.util.Collections.synchronizedList
import kotlin.reflect.KProperty

fun <T> asyncWithRefresh(executor: AsyncTaskExecutor, initializer: () -> T) = RefreshableAsync(executor, initializer)

class RefreshableAsync<T>(
    private val executor: AsyncTaskExecutor,
    private val initializer: () -> T
) {
    private var errorOrValue by atomic(Pair<Throwable?, T?>(null, null))
    private var future by atomic(executor.submit { update() })

    val stateChangeListeners: MutableList<() -> Unit> = synchronizedList(mutableListOf())

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        errorOrValue.run {
            first?.let { throw it } // if there's a stored error, throw it, otherwise...
            second?.let { return it } // ...if there's a stored value, return in, otherwise...
        }
        future.get() // ...there's a future that will set valueOrError when finished, so...
        errorOrValue.run {
            first?.let { throw it } // ...if there's a stored error, throw it, otherwise...
            return second!! // there's a value
        }
    }

    fun refresh() {
        future = executor.submit { update() }
    }

    private fun update() {
        errorOrValue = try {
            Pair(null, initializer())
        } catch (e: Throwable) {
            Pair(e, null)
        }
    }
}