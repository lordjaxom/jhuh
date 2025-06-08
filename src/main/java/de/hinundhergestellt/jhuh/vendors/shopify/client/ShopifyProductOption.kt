package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.shopify.admin.types.OptionCreateInput
import com.shopify.admin.types.OptionValueCreateInput
import com.shopify.admin.types.ProductOption

open class UnsavedShopifyProductOption(
    var name: String,
    val values: List<String>
) {
    override fun toString() =
        "UnsavedShopifyProductOption(name='$name')"

    internal fun toOptionCreateInput() =
        OptionCreateInput().also {
            it.name = name
            it.values = values.map { value -> value.toOptionValueCreateInput() }
        }
}

class ShopifyProductOption private constructor(
    val id: String,
    name: String,
    values: List<String>
) : UnsavedShopifyProductOption(
    name,
    values
) {
    internal constructor(option: ProductOption) : this(
        option.id,
        option.name,
        option.values
    )

    internal constructor(unsaved: UnsavedShopifyProductOption, id: String) : this(
        id,
        unsaved.name,
        unsaved.values
    )

    override fun toString() =
        "ShopifyProductOption(id='$id', name='$name')"
}

private fun String.toOptionValueCreateInput() = OptionValueCreateInput().also { it.name = this }
