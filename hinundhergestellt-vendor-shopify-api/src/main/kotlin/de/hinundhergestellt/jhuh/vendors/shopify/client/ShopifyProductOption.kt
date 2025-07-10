package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionValueCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOption

interface ShopifyProductOptionCommonFields {

    var name: String
    val values: List<String>
}

class UnsavedShopifyProductOption(
    override var name: String,
    override val values: List<String>
) : ShopifyProductOptionCommonFields {

    internal constructor(option: ProductOption) : this(
        option.name,
        option.values
    )

    override fun toString() =
        "UnsavedShopifyProductOption(name='$name')"

    internal fun toOptionCreateInput() =
        OptionCreateInput(
            name = name,
            values = values.map { OptionValueCreateInput(it) }
        )
}

class ShopifyProductOption internal constructor(
    private val unsaved: UnsavedShopifyProductOption,
    val id: String
) : ShopifyProductOptionCommonFields, HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    override var name by dirtyTracker.track(unsaved.name)
    override val values by unsaved::values

    internal constructor(option: ProductOption) : this(
        UnsavedShopifyProductOption(option),
        option.id
    )

    internal constructor(id: String, name: String, values: List<String>) : this(
        UnsavedShopifyProductOption(
            name,
            values
        ),
        id
    )

    override fun toString() =
        "ShopifyProductOption(id='$id', name='$name')"
}