@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.core

import java.util.Collections.synchronizedSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

class DirtyTracker {

    private val dirties = synchronizedSet(mutableSetOf<String>())
    private val collections = ConcurrentHashMap<String, Collection<*>>()

    val fields: List<String>
        get() = buildList {
            synchronized(dirties) {
                addAll(dirties)
                collections.forEach { name, collection ->
                    collection.forEachIndexed { index, entry ->
                        if (entry is HasDirtyTracker)
                            addAll(entry.dirtyTracker.fields.map { "$name[$index].$it" })
                    }
                }
            }
        }

    fun getDirtyAndReset(): Boolean {
        synchronized(dirties) {
            val result = dirties.isNotEmpty() or collections.values.map { it.getDirtyAndReset() }.any { it }
            dirties.clear()
            return result
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

    fun <V> track(tracked: KMutableProperty0<V>): ReadWriteProperty<Any?, V> =
        object : ReadWriteProperty<Any?, V> {
            override fun getValue(thisRef: Any?, property: KProperty<*>) = tracked.get()
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
                val oldValue = tracked.get()
                tracked.set(value)
                if (oldValue != tracked.get()) {
                    dirties += property.name
                }
            }
        }

    @JvmName("trackList")
    fun <V> track(tracked: List<V>): ReadOnlyProperty<Any?, List<V>> {
        return object : ReadOnlyProperty<Any?, List<V>> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): List<V> {
                collections.putIfAbsent(property.name, tracked)
                return tracked
            }
        }
    }

    @JvmName("trackMutableList")
    fun <V> track(tracked: MutableList<V>): ReadOnlyProperty<Any?, MutableList<V>> {
        return object : ReadOnlyProperty<Any?, MutableList<V>> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): MutableList<V> {
                collections.putIfAbsent(property.name, tracked)
                return DirtyTrackedMutableList(tracked) { dirties += property.name }
            }
        }
    }

    @Deprecated("Tracking a read only property is not supported", level = DeprecationLevel.ERROR)
    fun track(@Suppress("unused") tracked: KProperty0<*>) = Unit

    @Deprecated("This collection type is not implemented yet", level = DeprecationLevel.ERROR)
    fun track(@Suppress("unused") tracked: KMutableProperty0<out MutableCollection<*>>) = Unit

    @Deprecated("This collection type is not implemented yet", level = DeprecationLevel.ERROR)
    fun track(@Suppress("unused") tracked: Collection<*>) = Unit
}

interface HasDirtyTracker {

    val dirtyTracker: DirtyTracker
}

inline fun <T : HasDirtyTracker> T.ifDirty(block: (T) -> Unit) {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    if (dirtyTracker.getDirtyAndReset()) block(this)
}

private fun Collection<*>.getDirtyAndReset() =
    map { it is HasDirtyTracker && it.dirtyTracker.getDirtyAndReset() }.any { it }