package de.hinundhergestellt.jhuh.usecases.products

import de.hinundhergestellt.jhuh.backend.mapping.update
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldType
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.containsId
import de.hinundhergestellt.jhuh.vendors.shopify.client.findById
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import kotlin.streams.asSequence

private const val METAFIELD_NAMESPACE = "hinundhergestellt"
private val INVALID_TAG_CHARACTERS = """[^A-ZÄÖÜa-zäöüß0-9\\._ -]""".toRegex()

@Component
class ShopifyProductMapper(
    private val artooDataStore: ArtooDataStore,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val shopTexterService: ShopTexterService
) {
    fun mapToProduct(syncProduct: SyncProduct, artooMappedProduct: ArtooMappedProduct): UnsavedShopifyProduct {
        val product = Builder(syncProduct, artooMappedProduct).build()
        val shopTexts = runBlocking { shopTexterService.generateProductDetails(product) }
        product.descriptionHtml = shopTexts.description
        product.metafields.add(metafield("product_specs", shopTexts.technicalDetails, ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD))
        return product
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
                tags = productTags(),
                options = productOptions(),
                metafields = productMetafields(),
            )

        protected fun productTags(): Set<String> {
            val tags = sequence {
                val categoryIds = artooDataStore.findCategoriesByProduct(artooProduct).map { it.id }.toList()
                yieldAll(syncCategoryRepository.findByArtooIdIn(categoryIds).asSequence().flatMap { it.tags })
                yieldAll(syncProduct.tags)
                yield(syncProduct.vendor!!.name)
                yield(syncProduct.type!!)
            }
            return tags.map { it.replace(INVALID_TAG_CHARACTERS, "") }.toSet()
        }

        protected fun productMetafields() =
            mutableListOf(
                metafield("vendor_address", syncProduct.vendor!!.address!!, ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD),
                metafield("vendor_email", syncProduct.vendor!!.email!!, ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD),
            )

        private fun productOptions() =
            if (!artooProduct.hasOnlyDefaultVariant) listOf(UnsavedShopifyProductOption("Farbe", artooProduct.variations.map { it.name }))
            else listOf()
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
                    shopifyProduct::tags.update(productTags()) or
                    updateProductMetafields()
        }

        private fun updateProductMetafields(): Boolean {
            val metafields = productMetafields()
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