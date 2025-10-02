package de.hinundhergestellt.jhuh.tools

import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldType

private const val GOOGLE_METAFIELD_NAMESPACE = "mm-google-shopping"

private const val GOOGLE_CONDITION_KEY = "condition"

fun googleConditionNew() =
    ShopifyMetafield(GOOGLE_METAFIELD_NAMESPACE, GOOGLE_CONDITION_KEY, "new", ShopifyMetafieldType.STRING)