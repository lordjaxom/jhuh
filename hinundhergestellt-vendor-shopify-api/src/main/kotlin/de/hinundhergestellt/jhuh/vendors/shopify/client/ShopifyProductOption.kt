package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.client.ProductProjection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.LinkedMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.LinkedMetafieldCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.LinkedMetafieldUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOption

class ShopifyProductOption private constructor(
    internal var internalId: String?,
    var name: String,
    var linkedMetafield: LinkedMetafield?,
    val optionValues: MutableList<ShopifyProductOptionValue>
) {
    val id get() = internalId!!

    val isLinkedMetafield get() = linkedMetafield != null
    val values get() = optionValues.map { it.value }

    constructor(name: String, values: List<String>) : this(
        internalId = null,
        name = name,
        linkedMetafield = null,
        optionValues = values.asSequence().map { ShopifyProductOptionValue(name, it) }.toMutableList()
    )

    internal constructor(option: ProductOption) : this(
        option.id,
        option.name,
        option.linkedMetafield,
        option.optionValues.asSequence().map { ShopifyProductOptionValue(option.name, it) }.toMutableList()
    )

    override fun toString() =
        "ShopifyProductOption(id='$internalId', name='$name', isLinkedMetafield=$isLinkedMetafield, values=$values)"

    internal fun toOptionCreateInput(): OptionCreateInput {
        require(internalId == null) { "Cannot recreate existing product option" }
        return OptionCreateInput(
            name = name,
            linkedMetafield = linkedMetafield?.toLinkedMetafieldCreateInput(),
            values = optionValues.map { it.toOptionValueCreateInput() }
        )
    }

    internal fun toOptionUpdateInput() =
        OptionUpdateInput(
            id = id,
            name = name,
            linkedMetafield = linkedMetafield?.toLinkedMetafieldUpdateInput()
        )
}

fun Iterable<ShopifyProductOption>.findByLinkedMetafield(namespace: String, key: String) =
    firstOrNull { option -> option.linkedMetafield?.let { it.namespace == namespace && it.key == key } == true }

fun LinkedMetafield(namespace: String, key: String) =
    LinkedMetafield.Builder()
        .withNamespace(namespace)
        .withKey(key)
        .build()

internal fun ProductProjection.optionsForWrapper() =
    options {
        id; name
        linkedMetafield { namespace; key }
        optionValues { id; name; linkedMetafieldValue }
    }

internal fun LinkedMetafield.toLinkedMetafieldCreateInput() =
    LinkedMetafieldCreateInput(namespace!!, key!!)

internal fun LinkedMetafield.toLinkedMetafieldUpdateInput() =
    LinkedMetafieldUpdateInput(namespace!!, key!!)