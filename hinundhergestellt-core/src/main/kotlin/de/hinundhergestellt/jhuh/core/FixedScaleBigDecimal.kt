package de.hinundhergestellt.jhuh.core

import java.math.BigDecimal
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun fixedScale(value: BigDecimal, scale: Int) = FixedScaleBigDecimal(value, scale)

class FixedScaleBigDecimal(
    value: BigDecimal,
    private val scale: Int
) : ReadWriteProperty<Any?, BigDecimal> {

    private var value = value.setScale(scale)
    override fun getValue(thisRef: Any?, property: KProperty<*>): BigDecimal = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: BigDecimal) {
        this.value = value.setScale(scale)
    }
}
