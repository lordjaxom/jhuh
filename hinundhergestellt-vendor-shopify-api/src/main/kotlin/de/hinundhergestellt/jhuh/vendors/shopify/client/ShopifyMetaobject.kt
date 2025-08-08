package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Metaobject
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectField
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectFieldInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectUpdateInput

interface ShopifyMetaobjectCommonFields {

    val type: String
    var handle: String
    val fields: List<MetaobjectField>
}

class UnsavedShopifyMetaobject(
    override val type: String,
    override var handle: String,
    override val fields: List<MetaobjectField>
): ShopifyMetaobjectCommonFields {

    internal constructor(metaobject: Metaobject) : this(
        metaobject.type,
        metaobject.handle,
        metaobject.fields
    )

    override fun toString(): String {
        return "UnsavedShopifyMetaobject(type='$type', handle='$handle')"
    }

    internal fun toMetaobjectCreateInput() =
        MetaobjectCreateInput(
            handle = handle,
            type = type,
            fields = fields.map { it.toMetaobjectFieldInput() }
        )
}

class ShopifyMetaobject(
    private val unsaved: UnsavedShopifyMetaobject,
    val id: String,
): ShopifyMetaobjectCommonFields, HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    override val type by unsaved::type
    override var handle by dirtyTracker.track(unsaved::handle)
    override val fields by unsaved::fields

    internal constructor(metaobject: Metaobject): this(
        UnsavedShopifyMetaobject(metaobject),
        metaobject.id,
    )

    override fun toString(): String {
        return "ShopifyMetaobject(id='$id', handle='$handle', type='$type')"
    }

    internal fun toMetaobjectUpdateInput()=
        MetaobjectUpdateInput(
            handle = handle
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