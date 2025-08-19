@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.LinkedMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.LinkedMetafieldCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.LinkedMetafieldUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionValueCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionValueUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOptionValue
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

interface ShopifyProductOptionCommonFields {

    var name: String
    var linkedMetafield: LinkedMetafield?
    val optionValues: List<ProductOptionValue>
    val values: List<String>
}

class UnsavedShopifyProductOption(
    override var name: String,
    override var linkedMetafield: LinkedMetafield?,
    override val optionValues: MutableList<ProductOptionValue>,
) : ShopifyProductOptionCommonFields {

    override val values get() = optionValues.map { it.name }

    /**
     * Query constructor
     */
    internal constructor(option: ProductOption) : this(
        option.name,
        option.linkedMetafield,
        option.optionValues.toMutableList()
    )

    /**
     * Compatibility constructor
     */
    constructor(name: String, values: List<String>) : this(
        name,
        null,
        values.asSequence().map { ProductOptionValue(it) }.toMutableList()
    )

    override fun toString() =
        "UnsavedShopifyProductOption(name='$name')"

    internal fun toOptionCreateInput() =
        OptionCreateInput(
            name = name,
            linkedMetafield = linkedMetafield?.toLinkedMetafieldCreateInput(),
            values = optionValues.map { it.toOptionValueCreateInput() }
        )
}

class ShopifyProductOption internal constructor(
    private val unsaved: UnsavedShopifyProductOption,
    val id: String
) : ShopifyProductOptionCommonFields, HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    override var name by dirtyTracker.track(unsaved::name)
    override var linkedMetafield by dirtyTracker.track(unsaved::linkedMetafield)
    override val optionValues by dirtyTracker.track(unsaved.optionValues)
    override val values by unsaved::values

    /**
     * Query constructor
     */
    internal constructor(option: ProductOption) : this(
        UnsavedShopifyProductOption(option),
        option.id
    )

    /**
     * Test constructor
     */
    internal constructor(id: String, name: String, values: List<String>, linkedMetafield: LinkedMetafield? = null) : this(
        UnsavedShopifyProductOption(
            name,
            linkedMetafield,
            values.asSequence().map { ProductOptionValue(it) }.toMutableList()
        ),
        id
    )

    override fun toString() =
        "ShopifyProductOption(id='$id', name='$name')"

    internal fun toOptionUpdateInput() =
        OptionUpdateInput(
            id = id,
            name = name,
            linkedMetafield = linkedMetafield?.toLinkedMetafieldUpdateInput(),
        )
}

fun ProductOptionValue(name: String, linkedMetafieldValue: String? = null) =
    ProductOptionValue.Builder()
        .withName(name)
        .withLinkedMetafieldValue(linkedMetafieldValue)
        .build()

fun ProductOptionValue.update(block: ProductOptionValue.Builder.() -> Unit): ProductOptionValue {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return ProductOptionValue.Builder()
        .withId(id)
        .withName(name)
        .withLinkedMetafieldValue(linkedMetafieldValue)
        .apply(block)
        .build()
}

internal fun ProductOptionValue.toOptionValueCreateInput() =
    if (linkedMetafieldValue != null) OptionValueCreateInput(linkedMetafieldValue = linkedMetafieldValue)
    else OptionValueCreateInput(name = name)

internal fun ProductOptionValue.toOptionValueUpdateInput() =
    if (linkedMetafieldValue != null) OptionValueUpdateInput(id = id, linkedMetafieldValue = linkedMetafieldValue)
    else OptionValueUpdateInput(id = id, name = name)

fun LinkedMetafield(namespace: String, key: String) =
    LinkedMetafield.Builder()
        .withNamespace(namespace)
        .withKey(key)
        .build()

internal fun LinkedMetafield.toLinkedMetafieldCreateInput() =
    LinkedMetafieldCreateInput(namespace!!, key!!)

internal fun LinkedMetafield.toLinkedMetafieldUpdateInput() =
    LinkedMetafieldUpdateInput(namespace!!, key!!)