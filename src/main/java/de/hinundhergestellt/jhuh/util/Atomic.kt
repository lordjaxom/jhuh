package de.hinundhergestellt.jhuh.util

import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <T> atomicNullable(value: T? = null): ReadWriteProperty<Any?, T?> =
    object : ReadWriteProperty<Any?, T?> {
        private val ref = AtomicReference(value)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T? = ref.get()
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) = ref.set(value)
    }

fun <T> atomic(value: T): ReadWriteProperty<Any?, T> =
    object : ReadWriteProperty<Any?, T> {
        private val ref = AtomicReference(value)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = ref.get()
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = ref.set(value)
    }