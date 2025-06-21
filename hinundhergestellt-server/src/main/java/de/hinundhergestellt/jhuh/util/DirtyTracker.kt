package de.hinundhergestellt.jhuh.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class DirtyTracker {

    private val dirties = mutableSetOf<String>()

    fun getDirtyAndReset(): Boolean {
        val result = dirties.isNotEmpty()
        dirties.clear()
        return result
    }

    fun <T> track(initial: T): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            private var value: T = initial
            override fun getValue(thisRef: Any?, property: KProperty<*>) = value
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                if (this.value != value) {
                    this.value = value
                    dirties += property.name
                }
            }
        }

    fun <T> track(tracked: ReadWriteProperty<Any?, T>): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            override fun getValue(thisRef: Any?, property: KProperty<*>) = tracked.getValue(thisRef, property)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                val oldValue = tracked.getValue(thisRef, property)
                if (oldValue != value) {
                    tracked.setValue(thisRef, property, value)
                    dirties += property.name
                }
            }
        }
}