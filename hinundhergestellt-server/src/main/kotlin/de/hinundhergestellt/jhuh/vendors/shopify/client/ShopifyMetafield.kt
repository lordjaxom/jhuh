package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Metafield
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetafieldIdentifierInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetafieldInput

class ShopifyMetafield(
    val namespace: String,
    val key: String,
    var value: String,
    var type: ShopifyMetafieldType
) {
    internal constructor(metafield: Metafield) : this(
        metafield.namespace,
        metafield.key,
        metafield.value,
        ShopifyMetafieldType.valueOf(metafield.type.uppercase())
    )

    fun matchesId(other: ShopifyMetafield) = namespace == other.namespace && key == other.key

    internal fun toMetafieldInput() =
        MetafieldInput(
            namespace = namespace,
            key = key,
            value = value,
            type = type.name.lowercase()
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

enum class ShopifyMetafieldType {
    SINGLE_LINE_TEXT_FIELD,
    MULTI_LINE_TEXT_FIELD
}

fun List<ShopifyMetafield>.containsId(metafield: ShopifyMetafield) = any { it.matchesId(metafield) }

fun List<ShopifyMetafield>.findById(metafield: ShopifyMetafield) = find { it.matchesId(metafield) }