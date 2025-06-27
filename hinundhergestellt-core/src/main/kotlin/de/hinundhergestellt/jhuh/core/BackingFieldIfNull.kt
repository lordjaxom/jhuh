package de.hinundhergestellt.jhuh.core

import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

fun <T : Any, V> fieldIfNull(delegate: T?, property: KMutableProperty1<T, V>, value: V & Any) =
    BackingFieldIfNull(delegate, property, value)

class BackingFieldIfNull<T : Any, V>(
    private val delegate: T?,
    private val property: KMutableProperty1<T, V>,
    private var value: V & Any
) {
    operator fun getValue(ref: Any?, property: KProperty<*>): V & Any = delegate?.let { this.property.get(it) } ?: value
    operator fun setValue(ref: Any?, property: KProperty<*>, value: V & Any) {
        delegate?.let { this.property.set(it, value) } ?: run { this.value = value }
    }
}
