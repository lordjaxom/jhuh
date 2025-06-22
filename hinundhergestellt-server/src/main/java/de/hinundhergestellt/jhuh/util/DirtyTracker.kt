@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.util

import java.util.Collections.synchronizedSet
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class DirtyTracker {

    private val dirties = synchronizedSet(mutableSetOf<String>())

    fun getDirtyAndReset(): Boolean {
        synchronized(dirties) {
            if (dirties.isEmpty()) return false
            dirties.clear()
            return true
        }
    }

    fun <V> track(initial: V): ReadWriteProperty<Any?, V> =
        object : ReadWriteProperty<Any?, V> {
            private var value: V = initial
            override fun getValue(thisRef: Any?, property: KProperty<*>) = value
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
                if (this.value != value) {
                    this.value = value
                    dirties += property.name
                }
            }
        }

    fun <V> track(tracked: ReadWriteProperty<Any?, V>): ReadWriteProperty<Any?, V> =
        object : ReadWriteProperty<Any?, V> {
            override fun getValue(thisRef: Any?, property: KProperty<*>) = tracked.getValue(thisRef, property)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
                val oldValue = tracked.getValue(thisRef, property)
                tracked.setValue(thisRef, property, value)
                if (oldValue != tracked.getValue(thisRef, property)) {
                    dirties += property.name
                }
            }
        }
}

internal interface HasDirtyTracker {

    val dirtyTracker: DirtyTracker
}

internal suspend fun <T: HasDirtyTracker> T.ifDirty(block: suspend (T) -> Unit) {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    if (dirtyTracker.getDirtyAndReset()) block(this)
}