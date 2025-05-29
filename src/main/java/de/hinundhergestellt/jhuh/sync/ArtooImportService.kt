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
    private val syncVariantRepository: SyncVariantRepository,
    private val syncCategoryRepository: SyncCategoryRepository,
) {
    val rootCategories by artooDataStore::rootCategories

    fun getItemName(item: Any) =
        when (item) {
            is ArtooMappedCategory -> item.name
            is ArtooMappedProduct -> item.name
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun getItemTags(item: Any): String {
        val tags = when (item) {
            is ArtooMappedCategory -> syncCategoryRepository.findByArtooId(item.id)?.tags
            is ArtooMappedProduct -> syncProductRepository.findByArtooId(item.id)?.tags
            else -> throw IllegalStateException("Unexpected item $item")
        }
        return tags
            ?.sorted()
            ?.joinToString(", ")
            ?: ""
    }

    fun getItemVariations(item: Any) =
        when (item) {
            is ArtooMappedCategory -> null
            is SingleArtooMappedProduct -> 0
            is ArtooMappedProduct -> item.variations.size
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun isMarkedForSync(product: ArtooMappedProduct) =
        syncProductRepository.findByArtooId(product.id)?.synced ?: false

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
    fun updateItemTags(item: Any, value: String) {
        val tags = when (item) {
            is ArtooMappedCategory -> syncCategoryRepository.findByArtooId(item.id)!!.tags
            is ArtooMappedProduct -> syncProductRepository.findByArtooId(item.id)!!.tags
            else -> throw IllegalStateException("Unexpected item $item")
        }
        tags.clear()
        tags.addAll(value.splitToSequence(",").map { it.trim() }.filter { it.isNotEmpty() })
    }

    @Transactional
    fun markForSync(product: ArtooMappedProduct) {
        val syncProduct = syncProductRepository.findByArtooId(product.id)
            ?.apply { synced = true }
            ?: SyncProduct(product.id, null, mutableSetOf())
        syncProductRepository.save(syncProduct)
    }

    @Transactional
    fun unmarkForSync(product: ArtooMappedProduct) {
        syncProductRepository.findByArtooId(product.id)
            ?.apply { synced = false }
            ?.also { syncProductRepository.save(it) }
    }

    @Transactional
    fun synchronize() {
        try {
            shopifyDataStore.products.forEach { reconcileFromShopify(it) }
            syncProductRepository.findAllBy().forEach { reconcileFromArtoo(it) }
            artooDataStore.rootCategories.forEach { reconcileCategories(it) }
            synchronizeWithShopify()
        } catch (e: Exception) {
            logger.error(e) { "Synchronization failed" }
            throw e
        }
    }

    private fun reconcileFromShopify(shopifyProduct: ShopifyProduct) {
        val syncProduct = syncProductRepository.findByShopifyId(shopifyProduct.id)
            ?: SyncProduct(null, shopifyProduct.id, shopifyProduct.tags.toMutableSet())
        shopifyProduct.variants.forEach { reconcileFromShopify(it, syncProduct) }
        syncProductRepository.save(syncProduct) // for later retrieval in same transaction
    }

    private fun reconcileFromShopify(variant: ProductVariant, syncProduct: SyncProduct) {
        val syncVariant = syncVariantRepository.findByBarcode(variant.barcode)
            ?: SyncVariant(syncProduct, variant.barcode).also { syncProduct.variants.add(it) }

        require(syncVariant.product === syncProduct) { "SyncVariant.product does not match ShopifyProduct" }
    }

    private fun reconcileFromArtoo(syncProduct: SyncProduct) {
        val artooProduct = syncProduct.artooId
            ?.let { artooDataStore.findProductById(it) }
            ?: findArtooProductByVariantBarcodesAndReconcile(syncProduct)
            ?: return

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
                syncProduct.variants.add(SyncVariant(syncProduct, it.barcode!!))
            }

        syncProductRepository.save(syncProduct) // for later retrieval in same transaction
    }

    private fun findArtooProductByVariantBarcodesAndReconcile(syncProduct: SyncProduct) =
        syncProduct.variants
            .firstNotNullOfOrNull { artooDataStore.findProductByBarcode(it.barcode) }
            ?.also { syncProduct.artooId = it.id }
            ?: null.also {
                logger.info { "Product from SyncProduct ${syncProduct.id} not found in ready2order, mark for deletion" }
                syncProduct.variants.forEach { it.deleted = true }
            }

    private fun reconcileCategories(artooCategory: ArtooMappedCategory) {
        artooCategory.children.forEach { reconcileCategories(it) }

        if (syncCategoryRepository.findByArtooId(artooCategory.id) != null) {
            return
        }

        val tagsOfCategories = artooCategory.children.asSequence().mapNotNull { syncCategoryRepository.findByArtooId(it.id)?.tags }
        val tagsOfProducts = artooCategory.products.asSequence().mapNotNull { syncProductRepository.findByArtooId(it.id)?.tags }
        val commonTags = (tagsOfCategories + tagsOfProducts)
            .fold(null as Set<String>?) { acc, tags -> acc?.intersect(tags) ?: tags }
            .also { if (it.isNullOrEmpty()) return }!!

        syncCategoryRepository.save(SyncCategory(artooCategory.id, commonTags.toMutableSet()))

        artooCategory.children.asSequence()
            .mapNotNull { syncCategoryRepository.findByArtooId(it.id) }
            .onEach { it.tags.removeAll(commonTags) }
            .forEach { syncCategoryRepository.save(it) } // for later retrieval in same transaction
        artooCategory.products.asSequence()
            .mapNotNull { syncProductRepository.findByArtooId(it.id) }
            .onEach { it.tags.removeAll(commonTags) }
            .forEach { syncProductRepository.save(it) } // for later retrieval in same transaction
    }

    private fun synchronizeWithShopify() {

    }
}
