package de.hinundhergestellt.jhuh.util

import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

fun <T> lazyWithReset(initializer: () -> T): ResettableLazy<T> = ResettableLazy(initializer)

class ResettableLazy<T>(private val initializer: () -> T) {
    private val lazy: AtomicReference<Lazy<T>> = AtomicReference(lazy(initializer))
    operator fun getValue(ref: Any?, property: KProperty<*>): T = lazy.get().getValue(ref, property)
    fun reset(): Unit = lazy.set(lazy(initializer))
}
