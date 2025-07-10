package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.SelectedOption
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.VariantOptionValueInput

class ShopifyProductVariantOption(
    val name: String,
    value: String
) : HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    var value by dirtyTracker.track(value)

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