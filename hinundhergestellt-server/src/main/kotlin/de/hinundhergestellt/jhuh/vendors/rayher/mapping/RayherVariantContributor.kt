package de.hinundhergestellt.jhuh.vendors.rayher.mapping

import de.hinundhergestellt.jhuh.backend.mapping.VariantContributor
import de.hinundhergestellt.jhuh.backend.mapping.update
import de.hinundhergestellt.jhuh.vendors.rayher.datastore.RayherDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyWeight
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class RayherVariantContributor(
    private val dataStore: RayherDataStore
) : VariantContributor {

    override fun contribute(variant: UnsavedShopifyProductVariant) =
        dataStore.findByEan(variant.barcode)?.weight?.also { variant.weight = it.toShopifyWeight() } != null

    override fun contribute(variant: ShopifyProductVariant) =
        dataStore.findByEan(variant.barcode)?.weight?.let { variant::weight.update(it.toShopifyWeight()) } ?: false
}

private fun BigDecimal.toShopifyWeight() = ShopifyWeight(WeightUnit.GRAMS, this)