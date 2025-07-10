package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Location

data class ShopifyLocation(
    val id: String,
    val name: String,
    val isPrimary: Boolean,
    val fulfillsOnlineOrders: Boolean,
    val hasActiveInventory: Boolean,
    val shipsInventory: Boolean
) {
    internal constructor(location: Location) : this(
        location.id,
        location.name,
        location.isPrimary,
        location.fulfillsOnlineOrders,
        location.hasActiveInventory,
        location.shipsInventory
    )

    override fun toString() =
        "ShopifyLocation(id='$id', name='$name', isPrimary=$isPrimary)"
}