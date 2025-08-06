package de.hinundhergestellt.jhuh.usecases.shopify

import de.hinundhergestellt.jhuh.backend.mapping.VariantContributorService
import de.hinundhergestellt.jhuh.backend.mapping.update
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyWeight
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import org.springframework.stereotype.Component

@Component
class ShopifyVariantMapper(
    private val contributorService: VariantContributorService
) {

    fun mapToVariant(shopifyProduct: ShopifyProduct, artooVariation: ArtooMappedVariation, inventoryLocationId: String) =
        Builder(shopifyProduct, artooVariation, inventoryLocationId)
            .build()
            .also { contributorService.contribute(it) }

    fun updateVariant(shopifyVariant: ShopifyProductVariant, artooVariation: ArtooMappedVariation) =
        Updater(shopifyVariant, artooVariation).update() or
                contributorService.contribute(shopifyVariant)

    private inner class Builder(
        private val shopifyProduct: ShopifyProduct,
        private val artooVariation: ArtooMappedVariation,
        private val inventoryLocationId: String
    ) {
        fun build() =
            UnsavedShopifyProductVariant(
                artooVariation.itemNumber ?: "",
                artooVariation.barcode!!,
                artooVariation.price,
                ShopifyWeight(WeightUnit.GRAMS, 0.0),
                inventoryLocationId,
                artooVariation.stockValue.intValueExact(),
                variantOptions()
            )

        private fun variantOptions() =
            if (!shopifyProduct.hasOnlyDefaultVariant)
                listOf(ShopifyProductVariantOption(shopifyProduct.options[0].name, artooVariation.name))
            else listOf()
    }

    private inner class Updater(
        private val shopifyVariant: ShopifyProductVariant,
        private val artooVariation: ArtooMappedVariation
    ) {
        fun update(): Boolean {
            return updateVariantOptions() or
                    shopifyVariant::barcode.update(artooVariation.barcode!!) or
                    shopifyVariant::sku.update(artooVariation.itemNumber ?: "") or
                    shopifyVariant::price.update(artooVariation.price)
        }

        private fun updateVariantOptions() =
            if (!artooVariation.isDefaultVariant) shopifyVariant.options[0]::value.update(artooVariation.name)
            else false
    }
}