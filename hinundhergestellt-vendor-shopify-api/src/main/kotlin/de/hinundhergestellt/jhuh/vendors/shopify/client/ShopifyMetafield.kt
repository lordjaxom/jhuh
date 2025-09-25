package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Metafield
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetafieldIdentifierInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetafieldInput

class ShopifyMetafield(
    val namespace: String,
    val key: String,
    var value: String,
    var type: String
){
    internal constructor(metafield: Metafield) : this(
        metafield.namespace,
        metafield.key,
        metafield.value,
        metafield.type
    )

    internal fun toMetafieldInput() =
        MetafieldInput(
            namespace = namespace,
            key = key,
            value = value,
            type = type
        )

    internal fun toMetafieldIdentifierInput(ownerId: String) =
        MetafieldIdentifierInput(
            ownerId = ownerId,
            namespace = namespace,
            key = key
        )

    override fun toString(): String {
        return "ShopifyMetafield(namespace='$namespace', key='$key', value='$value', type='$type')"
    }
}

object ShopifyMetafieldType {
    const val STRING = "string"
    const val SINGLE_LINE_TEXT_FIELD = "single_line_text_field"
    const val MULTI_LINE_TEXT_FIELD = "multi_line_text_field"
    const val JSON = "json"
}

fun Iterable<ShopifyMetafield>.findById(namespace: String, key: String) = find { it.namespace == namespace && it.key == key }

fun Iterable<ShopifyMetafield>.findById(metafield: ShopifyMetafield) = findById(metafield.namespace, metafield.key)