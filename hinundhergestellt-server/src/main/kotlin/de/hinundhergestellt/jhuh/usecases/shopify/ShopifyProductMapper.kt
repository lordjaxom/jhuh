package de.hinundhergestellt.jhuh.usecases.shopify

import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.mapping.update
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldType
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.containsId
import de.hinundhergestellt.jhuh.vendors.shopify.client.findById
import org.springframework.stereotype.Component

private const val METAFIELD_NAMESPACE = "custom"

@Component
class ShopifyProductMapper(
    private val mappingService: MappingService
) {
    fun mapToProduct(syncProduct: SyncProduct, artooMappedProduct: ArtooMappedProduct): UnsavedShopifyProduct {
        return Builder(syncProduct, artooMappedProduct).build()
    }

    fun updateProduct(syncProduct: SyncProduct, artooMappedProduct: ArtooMappedProduct, shopifyProduct: ShopifyProduct) =
        Updater(syncProduct, artooMappedProduct, shopifyProduct).update()

    private open inner class Builder(
        protected val syncProduct: SyncProduct,
        protected val artooProduct: ArtooMappedProduct
    ) {
        fun build() =
            UnsavedShopifyProduct(
                title = artooProduct.description.ifEmpty { artooProduct.name },
                vendor = syncProduct.vendor!!.name,
                productType = syncProduct.type!!,
                descriptionHtml = syncProduct.descriptionHtml ?: "",
                tags = tags(),
                options = options(),
                metafields = metafields(),
            )

        protected fun tags(): Set<String> {
            return mappingService.inheritedTags(syncProduct, artooProduct) + syncProduct.tags
        }

        protected fun metafields() =
            mutableListOf(
                metafield("vendor_address", syncProduct.vendor!!.address!!, ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD),
                metafield("vendor_email", syncProduct.vendor!!.email!!, ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD),
                metafield("product_specs", technicalDetails(), ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD)
            )

        private fun options() =
            if (!artooProduct.hasOnlyDefaultVariant) listOf(UnsavedShopifyProductOption("Farbe", artooProduct.variations.map { it.name }))
            else listOf()

        private fun technicalDetails() =
            syncProduct.technicalDetails.joinToString(
                separator = "",
                prefix = "<table>",
                postfix = "</table>",
                transform = { "<tr><th>${it.name}</th><td>${it.value}</td></tr>" }
            )
    }

    private inner class Updater(
        syncProduct: SyncProduct,
        artooMappedProduct: ArtooMappedProduct,
        private val shopifyProduct: ShopifyProduct
    ) : Builder(syncProduct, artooMappedProduct) {

        fun update(): Boolean {
            require(shopifyProduct.hasOnlyDefaultVariant == artooProduct.hasOnlyDefaultVariant) { "Switching variants and standalone not supported yet" }
            return shopifyProduct::title.update(artooProduct.description.ifEmpty { artooProduct.name }) or
                    shopifyProduct::vendor.update(syncProduct.vendor!!.name) or
                    shopifyProduct::productType.update(syncProduct.type!!) or
                    shopifyProduct::descriptionHtml.update(syncProduct.descriptionHtml ?: "") or
                    shopifyProduct::tags.update(tags()) or
                    updateMetafields()
        }

        private fun updateMetafields(): Boolean {
            val metafields = metafields()
            return shopifyProduct.metafields.addAll(metafields.filter { !shopifyProduct.metafields.containsId(it) }) or
                    shopifyProduct.metafields.asSequence()
                        .mapNotNull { old -> metafields.findById(old)?.let { new -> old to new } }
                        .map { (old, new) -> old::value.update(new.value) or old::type.update(new.type) }
                        .toList() // enforce terminal operation
                        .any { it }
        }
    }
}

private fun metafield(key: String, value: String, type: ShopifyMetafieldType) = ShopifyMetafield(METAFIELD_NAMESPACE, key, value, type)