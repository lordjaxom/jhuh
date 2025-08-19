package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Metafield
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetafieldIdentifierInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetafieldInput

class ShopifyMetafield(
    val namespace: String,
    val key: String,
    value: String,
    type: ShopifyMetafieldType
): HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    var value by dirtyTracker.track(value)
    var type by dirtyTracker.track(type)

    internal constructor(metafield: Metafield) : this(
        metafield.namespace,
        metafield.key,
        metafield.value,
        ShopifyMetafieldType.fromValue(metafield.type)
    )

    fun matchesId(other: ShopifyMetafield) = namespace == other.namespace && key == other.key

    internal fun toMetafieldInput() =
        MetafieldInput(
            namespace = namespace,
            key = key,
            value = value,
            type = type.value
        )

    internal fun toMetafieldIdentifierInput(ownerId: String) =
        MetafieldIdentifierInput(
            ownerId = ownerId,
            namespace = namespace,
            key = key
        )

    override fun toString(): String {
        return "ShopifyMetafield(namespace='$namespace', key='$key', value='$value', type=$type)"
    }
}

enum class ShopifyMetafieldType(value: String? = null) {

    SINGLE_LINE_TEXT_FIELD,
    MULTI_LINE_TEXT_FIELD,
    STRING,
    LIST_METAOBJECT_REFERENCE("list.metaobject_reference");

    val value: String = value ?: name.lowercase()

    companion object {
        fun fromValue(value: String) = entries.first { it.value == value }
    }
}

fun List<ShopifyMetafield>.containsId(metafield: ShopifyMetafield) = any { it.matchesId(metafield) }

fun List<ShopifyMetafield>.findById(metafield: ShopifyMetafield) = find { it.matchesId(metafield) }