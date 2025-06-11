package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.shopify.admin.types.Metafield
import com.shopify.admin.types.MetafieldIdentifierInput
import com.shopify.admin.types.MetafieldInput

class ShopifyMetafield(
    val namespace: String,
    val key: String,
    var value: String,
    var type: ShopifyMetafieldType
) {
    internal constructor(metafield: Metafield): this(
        metafield.namespace,
        metafield.key,
        metafield.value,
        ShopifyMetafieldType.valueOf(metafield.type.uppercase())
    )

    internal fun toMetafieldInput() =
        MetafieldInput().also {
            it.namespace = namespace
            it.key = key
            it.value = value
            it.type = type.name.lowercase()
        }

    internal fun toMetafieldIdentifierInput(ownerId: String)=
        MetafieldIdentifierInput().also {
            it.ownerId = ownerId
            it.namespace = namespace
            it.key = key
        }

    override fun toString(): String {
        return "ShopifyMetafield(namespace='$namespace', key='$key', value='$value', type=$type)"
    }
}

enum class ShopifyMetafieldType {
    SINGLE_LINE_TEXT_FIELD,
    MULTI_LINE_TEXT_FIELD
}