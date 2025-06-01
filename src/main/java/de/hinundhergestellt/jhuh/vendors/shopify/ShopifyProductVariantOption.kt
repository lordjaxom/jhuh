package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.SelectedOption
import com.shopify.admin.types.VariantOptionValueInput

class ShopifyProductVariantOption(
    val name: String,
    val value: String
) {
    internal constructor(option: SelectedOption) : this(
        option.name,
        option.value
    )

    internal fun toVariantOptionValueInput() =
        VariantOptionValueInput().also {
            it.optionName = name
            it.name = value
        }
}