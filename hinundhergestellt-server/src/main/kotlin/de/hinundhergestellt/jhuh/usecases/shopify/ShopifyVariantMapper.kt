package de.hinundhergestellt.jhuh.usecases.shopify

import de.hinundhergestellt.jhuh.backend.mapping.update
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyWeight
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import org.springframework.stereotype.Component

@Component
class ShopifyVariantMapper(
    private val shopifyDataStore: ShopifyDataStore
) {

    fun mapToVariant(artooProduct: ArtooMappedProduct, syncVariant: SyncVariant, artooVariation: ArtooMappedVariation) =
        Builder(artooProduct, syncVariant, artooVariation).build()

    fun updateVariant(shopifyVariant: ShopifyProductVariant, artooVariation: ArtooMappedVariation) =
        Updater(shopifyVariant, artooVariation).update()

    private inner class Builder(
        private val artooProduct: ArtooMappedProduct,
        private val syncVariant: SyncVariant,
        private val artooVariation: ArtooMappedVariation
    ) {
        fun build() =
            UnsavedShopifyProductVariant(
                artooVariation.itemNumber ?: "",
                artooVariation.barcode!!,
                artooVariation.price,
                ShopifyWeight(WeightUnit.GRAMS, syncVariant.weight!!),
                shopifyDataStore.location.id,
                artooVariation.stockValue.intValueExact(),
                variantOptions()
            )

        private fun variantOptions() =
            if (!artooProduct.hasOnlyDefaultVariant)
                listOf(ShopifyProductVariantOption("Farbe", artooVariation.name)) // TODO: Option name
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