package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Metaobject
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectField
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectFieldInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectUpdateInput
import java.time.OffsetDateTime

class ShopifyMetaobject private constructor(
    internal var internalId: String?,
    val type: String,
    val handle: String,
    val fields: MutableList<MetaobjectField>,
    val updatedAt: OffsetDateTime?
) {
    val id get() = internalId!!

    constructor(type: String, handle: String, fields: List<MetaobjectField>) : this(
        internalId = null,
        type = type,
        handle = handle,
        fields = fields.toMutableList(),
        updatedAt = null
    )

    internal constructor(metaobject: Metaobject) : this(
        metaobject.id,
        metaobject.type,
        metaobject.handle,
        metaobject.fields.toMutableList(),
        metaobject.updatedAt
    )

    override fun toString(): String {
        return "ShopifyMetaobject(id='$internalId', handle='$handle', type='$type')"
    }

    internal fun toMetaobjectCreateInput(): MetaobjectCreateInput {
        require(internalId == null) { "Cannot recreate existing metaobject" }
        return MetaobjectCreateInput(
            handle = handle,
            type = type,
            fields = fields
                .filter { it.value != null }
                .map { it.toMetaobjectFieldInput() }
        )
    }

    internal fun toMetaobjectUpdateInput() =
        MetaobjectUpdateInput(
            handle = handle,
            fields = fields
                .filter { it.value != null }
                .map { it.toMetaobjectFieldInput() }
        )
}

fun MetaobjectField(key: String, value: String?) =
    MetaobjectField.Builder()
        .withKey(key)
        .withValue(value)
        .build()

private fun MetaobjectField.toMetaobjectFieldInput() =
    MetaobjectFieldInput(
        key = key,
        value = value!!
    )