package de.hinundhergestellt.jhuh.usecases.shopify

import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
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

    fun mapToVariant(
        syncProduct: SyncProduct,
        artooProduct: ArtooMappedProduct,
        syncVariant: SyncVariant,
        artooVariation: ArtooMappedVariation
    ) = Builder(syncProduct, artooProduct, syncVariant, artooVariation).build()

    private inner class Builder(
        private val syncProduct: SyncProduct,
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

        private fun variantOptions() = buildList {
            if (!artooProduct.hasOnlyDefaultVariant)
                add(ShopifyProductVariantOption(syncProduct.optionName!!, artooVariation.name))
        }
    }
}