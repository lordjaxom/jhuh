package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionValueCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionValueUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOptionValue
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.SelectedOption
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.VariantOptionValueInput

class ShopifyProductOptionValue private constructor(
    internal var internalId: String?,
    val name: String,
    val value: String,
    var linkedMetafieldValue: String?
) {
    val id get() = internalId!!

    val isLinkedMetafieldValue get() = linkedMetafieldValue != null

    constructor(name: String, value: String) : this(
        internalId = null,
        name = name,
        value = value,
        linkedMetafieldValue = null
    )

    internal constructor(name: String, value: ProductOptionValue) : this(
        internalId = value.id,
        name = name,
        value = value.name,
        linkedMetafieldValue = value.linkedMetafieldValue
    )

    internal constructor(option: SelectedOption) : this(
        option.optionValue.id,
        option.name,
        option.value,
        option.optionValue.linkedMetafieldValue,
    )

    override fun toString() =
        "ShopifyProductOptionValue(id='$internalId', name='$name', value='$value', isLinkedMetafieldValue=$isLinkedMetafieldValue)"

    internal fun toOptionValueCreateInput(): OptionValueCreateInput {
        require(internalId == null) { "Cannot recreate existing product option value" }
        return if (linkedMetafieldValue == null) OptionValueCreateInput(name = value)
        else OptionValueCreateInput(linkedMetafieldValue = linkedMetafieldValue)
    }

    internal fun toOptionValueUpdateInput() =
        if (linkedMetafieldValue == null) OptionValueUpdateInput(id = id, name = value)
        else OptionValueUpdateInput(id = id, linkedMetafieldValue = linkedMetafieldValue)

    internal fun toVariantOptionValueInput() =
        VariantOptionValueInput(name = value, optionName = name) // TODO is either create or rename (update)?
}

fun Iterable<ShopifyProductOptionValue>.findByOption(option: ShopifyProductOption) = firstOrNull { it.name == option.name }

internal val Iterable<ShopifyProductOptionValue>.variantTitle get() = joinToString(" / ") { it.value }.ifEmpty { "Default Title" }