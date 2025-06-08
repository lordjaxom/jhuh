package de.hinundhergestellt.jhuh.util

import arrow.atomic.Atomic
import arrow.atomic.update
import arrow.core.Either
import arrow.core.getOrElse
import org.springframework.core.task.AsyncTaskExecutor
import java.util.Collections.synchronizedList
import kotlin.reflect.KProperty

fun <T> asyncWithRefresh(executor: AsyncTaskExecutor, initializer: () -> T) = RefreshableAsync(executor, initializer)

class RefreshableAsync<T>(
    private val executor: AsyncTaskExecutor,
    private val initializer: () -> T
) {
    private var errorOrValue by atomicNullable<Either<Throwable, T>>()
    private val future = Atomic(executor.submit { update() })

    val stateChangeListeners: MutableList<() -> Unit> = synchronizedList(mutableListOf())

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        errorOrValue
            ?.getOrElse { throw it } // if there's a stored error, throw it, otherwise...
            ?.let { return it } // ...if there's a stored value, return it, otherwise...
        future.get().get() // ...there's a future that will set valueOrError when finished, so...
        return errorOrValue!!.getOrElse { throw it }  // ...if there's a stored error, throw it, otherwise there's a value to return
    }

    fun refresh() {
        future.update { if (it.isDone) executor.submit { update() } else it }
    }

    private fun update() {
        errorOrValue = try {
            Either.Right(initializer())
        } catch (e: Throwable) {
            Either.Left(e)
        }
        synchronized(stateChangeListeners) {
            stateChangeListeners.forEach { it() }
        }
    }
}