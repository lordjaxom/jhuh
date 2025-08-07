package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Weight
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import java.math.BigDecimal

data class ShopifyWeight(
    val unit: WeightUnit,
    val value: BigDecimal
) {
    internal constructor(weight: Weight) : this(
        unit = weight.unit,
        value = weight.value
    )

    fun toWeightInput() =
        WeightInput(
            unit = unit,
            value = value
        )
}