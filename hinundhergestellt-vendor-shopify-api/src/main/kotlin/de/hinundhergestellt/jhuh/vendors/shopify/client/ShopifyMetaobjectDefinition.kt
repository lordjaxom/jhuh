package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetaobjectDefinition

class ShopifyMetaobjectDefinition private constructor(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    var metaobjects: List<ShopifyMetaobject>
) {

    internal constructor(definition: MetaobjectDefinition, metaobjects: List<ShopifyMetaobject>) : this(
        definition.id,
        definition.name,
        definition.description!!,
        definition.type,
        metaobjects
    )

    override fun toString(): String {
        return "ShopifyMetaobjectDefinition(id='$id', name='$name', description='$description', type='$type')"
    }
}