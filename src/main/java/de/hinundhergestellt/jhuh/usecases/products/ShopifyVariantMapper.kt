package de.hinundhergestellt.jhuh.usecases.products

import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import kotlin.reflect.KMutableProperty0

private val logger = KotlinLogging.logger {}

@Component
class ShopifyVariantMapper {

    fun mapToVariant(shopifyProduct: ShopifyProduct, artooVariation: ArtooMappedVariation) =
        Builder(shopifyProduct, artooVariation).build()

    fun updateVariant(shopifyVariant: ShopifyProductVariant, artooVariation: ArtooMappedVariation) =
        Updater(shopifyVariant, artooVariation).update()

    private inner class Builder(
        private val shopifyProduct: ShopifyProduct,
        private val artooVariation: ArtooMappedVariation
    ) {
        fun build() =
            UnsavedShopifyProductVariant(
                artooVariation.itemNumber ?: "",
                artooVariation.barcode!!,
                artooVariation.price,
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
                    updateProperty(shopifyVariant::sku, artooVariation.itemNumber ?: "") or
                    updateProperty(shopifyVariant::price, artooVariation.price)
        }

        private fun updateVariantOptions() =
            if (!artooVariation.isDefaultVariant) updateProperty(shopifyVariant.options[0]::value, artooVariation.name)
            else false
    }
}
