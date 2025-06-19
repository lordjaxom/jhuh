package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.SelectedOption
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.VariantOptionValueInput

class ShopifyProductVariantOption(
    val name: String,
    var value: String
) {
    internal constructor(option: SelectedOption) : this(
        option.name,
        option.value
    )

    override fun toString() =
        "ShopifyProductVariantOption(name='$name', value='$value')"

    internal fun toVariantOptionValueInput() =
        VariantOptionValueInput(
            optionName = name,
            name = value
        )
}