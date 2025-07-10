package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionValueCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOption

open class UnsavedShopifyProductOption(
    var name: String,
    val values: List<String>
) {
    override fun toString() =
        "UnsavedShopifyProductOption(name='$name')"

    internal fun toOptionCreateInput() =
        OptionCreateInput(
            name = name,
            values = values.map { OptionValueCreateInput(it) }
        )
}

class ShopifyProductOption : UnsavedShopifyProductOption {

    val id: String

    internal constructor(option: ProductOption) : super(
        option.name,
        option.values
    ) {
        id = option.id
    }

    internal constructor(unsaved: UnsavedShopifyProductOption, id: String) : super(
        unsaved.name,
        unsaved.values
    ) {
        this.id = id
    }

    internal constructor(id: String, name: String, values: List<String>) : super(
        name,
        values
    ) {
        this.id = id
    }

    override fun toString() =
        "ShopifyProductOption(id='$id', name='$name')"
}