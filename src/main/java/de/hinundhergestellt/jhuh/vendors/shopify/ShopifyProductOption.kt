package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.OptionCreateInput
import com.shopify.admin.types.OptionValueCreateInput
import com.shopify.admin.types.ProductOption

class ShopifyProductOption private constructor(
    var id: String?,
    var name: String,
    val values: List<String>
) {
    constructor(
        name: String,
        values: List<String>
    ) : this(
        null,
        name,
        values
    )

    internal constructor(option: ProductOption) : this(
        option.id,
        option.name,
        option.values
    )

    internal fun toOptionCreateInput() =
        OptionCreateInput().also {
            it.name = name
            it.values = values.map { value -> value.toOptionValueCreateInput() }
        }
}

private fun String.toOptionValueCreateInput() =
    OptionValueCreateInput().also {
        it.name = this
    }
