package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.SelectedOption
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.VariantOptionValueInput

class ShopifyProductVariantOption(
    val name: String,
    value: String,
    linkedMetafieldValue: String? = null,
    private val id: String? = null
) : HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    var value by dirtyTracker.track(value)
    var linkedMetafieldValue by dirtyTracker.track(linkedMetafieldValue)

    internal constructor(option: SelectedOption) : this(
        option.name,
        option.value,
        option.optionValue.linkedMetafieldValue,
        option.optionValue.id
    )

    override fun toString() =
        "ShopifyProductVariantOption(name='$name', value='$value')"

    internal fun toVariantOptionValueInput() =
        if (linkedMetafieldValue != null) VariantOptionValueInput(id = id, optionName = name, linkedMetafieldValue = linkedMetafieldValue)
        else VariantOptionValueInput(optionName = name, name = value)
}