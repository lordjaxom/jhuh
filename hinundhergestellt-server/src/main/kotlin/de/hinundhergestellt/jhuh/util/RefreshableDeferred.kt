package de.hinundhergestellt.jhuh.util

import arrow.atomic.Atomic
import arrow.core.Either
import arrow.core.getOrElse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KProperty

fun <T> deferredWithRefresh(coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO), initializer: suspend () -> T) =
    RefreshableDeferred(coroutineScope, initializer)

class RefreshableDeferred<T>(
    private val coroutineScope: CoroutineScope,
    private val initializer: suspend () -> T
) {
    private var errorOrValue by atomicNullable<Either<Throwable, T>>()
    private val deferred = Atomic(update())

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        runBlocking { awaitIfNecessary() }
        return errorOrValue!!.getOrElse { throw it }
    }

    suspend fun get(): T {
        awaitIfNecessary()
        return errorOrValue!!.getOrElse { throw it }
    }

    fun refresh(): Deferred<Unit> = deferred.updateAndGet { if (it.isCompleted) update() else it }

    suspend fun refreshAndAwait() = refresh().await()

    private fun update() = coroutineScope.async {
        errorOrValue = try {
            Either.Right(initializer())
        } catch (e: Throwable) {
            Either.Left(e)
        }
    }

    private suspend fun awaitIfNecessary() = deferred.get().apply { if (errorOrValue == null) await() }
}