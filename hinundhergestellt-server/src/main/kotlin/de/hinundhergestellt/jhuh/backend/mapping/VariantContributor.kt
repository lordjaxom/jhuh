package de.hinundhergestellt.jhuh.backend.mapping

import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant

interface VariantContributor {

    fun contribute(variant: UnsavedShopifyProductVariant): Boolean
    fun contribute(variant: ShopifyProductVariant): Boolean
}