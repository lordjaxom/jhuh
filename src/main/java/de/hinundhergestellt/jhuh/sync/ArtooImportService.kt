package de.hinundhergestellt.jhuh.sync

import com.shopify.admin.types.ProductVariant
import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.service.ready2order.ArtooDataStore
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedCategory
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedProduct
import de.hinundhergestellt.jhuh.service.ready2order.SingleArtooMappedProduct
import de.hinundhergestellt.jhuh.service.shopify.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
@VaadinSessionScope
class ArtooImportService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository
) {
    val rootCategories by artooDataStore::rootCategories

    fun getItemName(item: Any) =
        when (item) {
            is ArtooMappedCategory -> item.name
            is ArtooMappedProduct -> item.name
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun getItemVariations(item: Any) =
        when (item) {
            is ArtooMappedCategory -> null
            is SingleArtooMappedProduct -> 0
            is ArtooMappedProduct -> item.variations.size
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun isMarkedForSync(product: ArtooMappedProduct) =
        product.variations
            .any { variation -> variation.barcode?.let { syncVariantRepository.existsByBarcode(it) } ?: false }

    fun filterByReadyToSync(item: Any) =
        when (item) {
            is ArtooMappedCategory -> item.containsReadyForSync()
            is ArtooMappedProduct -> item.isReadyForSync
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun filterByMarkedForSync(item: Any): Boolean =
        when (item) {
            is ArtooMappedCategory -> (item.children.asSequence() + item.products.asSequence()).any { filterByMarkedForSync(it) }
            is ArtooMappedProduct -> isMarkedForSync(item)
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun filterByMarkedWithErrors(item: Any): Boolean =
        when (item) {
            is ArtooMappedCategory -> (item.children.asSequence() + item.products.asSequence()).any { filterByMarkedWithErrors(it) }
            is ArtooMappedProduct -> isMarkedForSync(item) && !item.isReadyForSync
            else -> throw IllegalStateException("Unexpected item $item")
        }

    @Transactional
    fun markForSync(product: ArtooMappedProduct) {
        // TODO: Tags!
        val syncProduct = SyncProduct(product.id, null, listOf())
        product.variations.forEach { SyncVariant(syncProduct, it.barcode!!) }
        syncProductRepository.save(syncProduct)
    }

    @Transactional
    fun unmarkForSync(product: ArtooMappedProduct) {
        syncProductRepository.deleteByArtooId(product.id)
    }

    @Transactional
    fun syncWithShopify() {
        shopifyDataStore.products.forEach { reconcileFromShopify(it) }
        syncProductRepository.findAllBy().forEach { reconcileFromArtoo(it) }
    }

    private fun reconcileFromShopify(shopifyProduct: ShopifyProduct) {
        val syncProduct = syncProductRepository.findByShopifyId(shopifyProduct.id)
            ?: syncProductRepository.save(SyncProduct(null, shopifyProduct.id, shopifyProduct.tags))
        shopifyProduct.variants.forEach { reconcileFromShopify(it, syncProduct) }
    }

    private fun reconcileFromShopify(variant: ProductVariant, syncProduct: SyncProduct) {
        val syncVariant = syncVariantRepository.findByBarcode(variant.barcode)
            ?: SyncVariant(syncProduct, variant.barcode)

        require(syncVariant.product === syncProduct) { "SyncVariant.product does not match ShopifyProduct" }
    }

    private fun reconcileFromArtoo(syncProduct: SyncProduct) {
        val artooProduct = syncProduct.artooId
            ?.let { artooDataStore.findProductById(it) }
            ?: run {
                syncProduct.variants.firstNotNullOfOrNull { artooDataStore.findProductByBarcode(it.barcode) }
                    ?.also { syncProduct.artooId = it.id }
                    ?: run {
                        logger.info { "Product from SyncProduct ${syncProduct.id} no longer in ready2order, marked for deletion" }
                        syncProduct.variants.forEach { it.deleted = true }
                        return
                    }
            }

        syncProduct.variants.asSequence()
            .filter { artooDataStore.findProductByBarcode(it.barcode) == null }
            .forEach {
                logger.info { "Variant of ${artooProduct.name} with barcode ${it.barcode} no longer in ready2order, mark for deletion" }
                it.deleted = true
            }

        artooProduct.variations.asSequence()
            .filter { it.barcode != null }
            .filter { variation -> syncProduct.variants.none { it.barcode == variation.barcode } }
            .forEach {
                logger.info { "Variation ${it.name} not in Shopify, mark for synchronisation" }
                SyncVariant(syncProduct, it.barcode!!)
            }
    }
}
