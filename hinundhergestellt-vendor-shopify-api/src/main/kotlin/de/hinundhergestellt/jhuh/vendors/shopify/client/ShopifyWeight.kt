package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.fixedScale
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Weight
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import java.math.BigDecimal

class ShopifyWeight(
    val unit: WeightUnit,
    value: BigDecimal
) {
    val value by fixedScale(value, 2)

    internal constructor(weight: Weight) : this(
        unit = weight.unit,
        value = weight.value
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ShopifyWeight
        return unit == other.unit &&
                value == other.value
    }

    override fun hashCode() =
        unit.hashCode()
            .let { 31 * it + value.hashCode() }

    override fun toString(): String {
        return "ShopifyWeight(unit=$unit, value=$value)"
    }

    fun toWeightInput() =
        WeightInput(
            unit = unit,
            value = value
        )
}