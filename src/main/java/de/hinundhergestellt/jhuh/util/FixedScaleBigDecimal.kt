package de.hinundhergestellt.jhuh.util

import java.math.BigDecimal
import kotlin.reflect.KProperty

fun fixedScale(value: BigDecimal, scale: Int) = FixedScaleBigDecimal(value, scale)

class FixedScaleBigDecimal(
    value: BigDecimal,
    private val scale: Int
) {
    private var value = value.setScale(scale)
    operator fun getValue(ref: Any?, property: KProperty<*>): BigDecimal = value
    operator fun setValue(ref: Any?, property: KProperty<*>, value: BigDecimal) {
        this.value = value.setScale(scale)
    }
}
