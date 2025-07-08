package de.hinundhergestellt.jhuh.backend.mapping

import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import org.springframework.stereotype.Service

@Service
class VariantContributorService(
    private val variantContributors: List<VariantContributor>
) {
    fun contribute(variant: UnsavedShopifyProductVariant) = variantContributors.map { it.contribute(variant) }.any { it }
    fun contribute(variant: ShopifyProductVariant) = variantContributors.map { it.contribute(variant) }.any { it }
}