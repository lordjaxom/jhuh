package de.hinundhergestellt.jhuh.sync

import com.shopify.admin.types.ProductOption
import com.shopify.admin.types.SelectedOption
import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.service.ready2order.ArtooDataStore
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedCategory
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedProduct
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedVariation
import de.hinundhergestellt.jhuh.service.ready2order.SingleArtooMappedProduct
import de.hinundhergestellt.jhuh.service.shopify.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductVariant
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.sequences.forEach

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

    fun getItemVendor(item: Any) =
        when (item) {
            is ArtooMappedCategory -> ""
            is ArtooMappedProduct -> syncProductRepository.findByArtooId(item.id)?.vendor ?: ""
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun getItemType(item: Any) =
        when (item) {
            is ArtooMappedCategory -> ""
            is ArtooMappedProduct -> syncProductRepository.findByArtooId(item.id)?.type ?: ""
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

    fun filterBy(item: Any, markedForSync: Boolean, readyForSync: Boolean, markedWithErrors: Boolean, text: String): Boolean =
        when (item) {
            is ArtooMappedCategory -> (item.children.asSequence() + item.products.asSequence())
                .any { filterBy(it, markedForSync, readyForSync, markedWithErrors, text) }

            is ArtooMappedProduct -> (!markedForSync || isMarkedForSync(item)) &&
                    (!readyForSync || item.isReadyForSync) &&
                    (!markedWithErrors || isMarkedForSync(item) && !item.isReadyForSync) &&
                    (text.isEmpty() || item.name.contains(text, ignoreCase = true))

            else -> throw IllegalStateException("Unexpected item $item")
        }

    @Transactional
    fun updateItem(item: Any, vendor: String?, type: String?, tags: String) {
        val splitTags = tags.splitToSequence(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        when (item) {
            is ArtooMappedCategory -> {
                val syncCategory = syncCategoryRepository.findByArtooId(item.id)!!
                syncCategory.tags = splitTags
                syncCategoryRepository.save(syncCategory)

                if (vendor == null && type == null) {
                    return
                }
                item.findAllProducts().forEach { artooProduct ->
                    val syncProduct = syncProductRepository.findByArtooId(artooProduct.id)
                        ?.also { if (vendor != null) it.vendor = vendor; if (type != null) it.type = type }
                        ?: SyncProduct(artooId = artooProduct.id, vendor = vendor, type = type, synced = false)
                    syncProductRepository.save(syncProduct)
                }
            }

            is ArtooMappedProduct -> {
                val syncProduct = syncProductRepository.findByArtooId(item.id)!!
                syncProduct.vendor = vendor
                syncProduct.type = type
                syncProduct.tags = splitTags
                syncProductRepository.save(syncProduct)
            }

            else -> throw IllegalArgumentException("Unexpected item $item")
        }
    }

    @Transactional
    fun markForSync(product: ArtooMappedProduct) {
        val syncProduct = syncProductRepository.findByArtooId(product.id)
            ?.apply { synced = true }
            ?: SyncProduct(artooId = product.id, synced = true)
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
            artooDataStore.findAllProducts().forEach { reconcileFromArtoo(it) }
            artooDataStore.rootCategories.forEach { reconcileCategories(it) }

            syncProductRepository.findAllBy().forEach { synchronizeWithShopify(it) }
        } catch (e: Exception) {
            logger.error(e) { "Synchronization failed" }
            throw e
        }
    }

    private fun reconcileFromShopify(shopifyProduct: ShopifyProduct) {
        // all products in Shopify are considered synced
        val syncProduct = syncProductRepository.findByShopifyId(shopifyProduct.id!!) ?: toSyncProduct(shopifyProduct)
        shopifyProduct.variants.forEach { reconcileFromShopify(it, syncProduct) }
        syncProductRepository.save(syncProduct) // for later retrieval in same transaction
    }

    private fun reconcileFromShopify(shopifyVariant: ShopifyProductVariant, syncProduct: SyncProduct) {
        syncVariantRepository.findByBarcode(shopifyVariant.barcode)
            ?.also { require(it.product === syncProduct) { "SyncVariant.product does not match ShopifyVariant.product" } }
            ?: SyncVariant(syncProduct, shopifyVariant.barcode).also { syncProduct.variants.add(it) }
    }

    private fun reconcileFromArtoo(artooProduct: ArtooMappedProduct) {
        // products in ready2order are only synced when there's a marker, but make sure all variations are known
        val syncProduct = syncProductRepository.findByArtooId(artooProduct.id)
            ?: syncProductRepository.findByVariantsBarcodeIn(artooProduct.barcodes)
                ?.also { it.artooId = artooProduct.id } // save missing id
            ?: return
        artooProduct.variations.forEach { reconcileFromArtoo(it, syncProduct) }
        syncProductRepository.save(syncProduct) // for later retrieval in same transaction
    }

    private fun reconcileFromArtoo(artooVariation: ArtooMappedVariation, syncProduct: SyncProduct) {
        val barcode = artooVariation.barcode ?: return
        syncVariantRepository.findByBarcode(barcode)
            ?.also { require(it.product === syncProduct) { "SyncVariant.product does not match ArtooVariation.product" } }
            ?: SyncVariant(syncProduct, barcode).also { syncProduct.variants.add(it) }
    }

    private fun reconcileCategories(artooCategory: ArtooMappedCategory) {
        artooCategory.children.forEach { reconcileCategories(it) }

        if (syncCategoryRepository.findByArtooId(artooCategory.id) != null) {
            return
        }

        val tagsOfCategories = artooCategory.children.asSequence().mapNotNull { syncCategoryRepository.findByArtooId(it.id)?.tags }
        val tagsOfProducts = artooCategory.products.asSequence().mapNotNull { syncProductRepository.findByArtooId(it.id)?.tags }
        val commonTags = (tagsOfCategories + tagsOfProducts).fold(null as Set<String>?) { acc, tags -> acc?.intersect(tags) ?: tags }
        if (commonTags.isNullOrEmpty()) {
            return
        }

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

    private fun synchronizeWithShopify(syncProduct: SyncProduct) {
        // TODO: Products in ready2order with no barcodes at all?
        val artooProduct = syncProduct.artooId?.let { artooDataStore.findProductById(it) }
        var shopifyProduct = syncProduct.shopifyId?.let { shopifyDataStore.findProductById(it) }

        if (artooProduct == null || !syncProduct.synced) {
            require(shopifyProduct != null) { "SyncProduct vanished from both ready2order and Shopify" }
            logger.info { "Product ${shopifyProduct!!.title} no longer in ready2order or not synced, delete from Shopify" }
            shopifyDataStore.delete(shopifyProduct)
            if (artooProduct == null) {
                syncProductRepository.delete(syncProduct)
            }
            return
        }

        if (shopifyProduct == null) {
            logger.info { "Product ${artooProduct.name} only in ready2order, create in Shopify" }
            shopifyProduct = buildShopifyProduct(artooProduct, syncProduct)
            shopifyDataStore.create(shopifyProduct)
        } else {
            // Update product if necessary
        }

        val bulkOperations = syncProduct.variants.map { synchronizeWithShopify(it, artooProduct, shopifyProduct) }
        bulkOperations
            .mapNotNull { (it as? VariantBulkOperation.Delete)?.variant }
            .also { if (it.isNotEmpty()) shopifyDataStore.delete(shopifyProduct, it) }
        bulkOperations
            .mapNotNull { (it as? VariantBulkOperation.Create)?.variant }
            .also { if (it.isNotEmpty()) shopifyDataStore.create(shopifyProduct, it) }
    }

    private fun synchronizeWithShopify(syncVariant: SyncVariant, artooProduct: ArtooMappedProduct, shopifyProduct: ShopifyProduct)
            : VariantBulkOperation? {
        val artooVariation = artooProduct.findVariationByBarcode(syncVariant.barcode)
        val shopifyVariant = shopifyProduct.findVariantByBarcode(syncVariant.barcode)

        if (artooVariation == null) {
            require(shopifyVariant != null) { "SyncVariant vanished from both ready2order and Shopify" }
            logger.info { "Variant ${shopifyVariant.title} of ${shopifyProduct.title} no longer in ready2order, delete from Shopify" }
            syncVariant.product.variants.remove(syncVariant)
            return VariantBulkOperation.Delete(shopifyVariant)
        }

        if (shopifyVariant == null) {
            logger.info { "Variant ${artooVariation.name} only in ready2order, create in Shopify" }
            val newShopifyVariant = buildShopifyVariant(shopifyProduct, artooProduct, artooVariation)
            return VariantBulkOperation.Create(newShopifyVariant)
        }

        // Update variant if necessary

        return null
    }

    private fun buildShopifyProduct(
        artooProduct: ArtooMappedProduct,
        syncProduct: SyncProduct
    ): ShopifyProduct {
        val categoryTags = artooDataStore.findAllCategoriesByProduct(artooProduct)
            .mapNotNull { syncCategoryRepository.findByArtooId(it.id)?.tags }
            .flatten()
            .toSet()
        val tags = categoryTags + syncProduct.tags + setOf(syncProduct.vendor!!, syncProduct.type!!)
        val options = when (artooProduct) {
            is SingleArtooMappedProduct -> listOf()
            else -> listOf(ProductOption().apply {
                name = "Farbe" // TODO
                values = artooProduct.variations.map { it.name.removePrefix(artooProduct.name).trim() }
            })
        }
        return ShopifyProduct(
            artooProduct.name,
            syncProduct.vendor!!,
            syncProduct.type!!,
            tags.toList(),
            options
        )
    }

    private fun buildShopifyVariant(
        shopifyProduct: ShopifyProduct,
        artooProduct: ArtooMappedProduct,
        artooVariation: ArtooMappedVariation
    ): ShopifyProductVariant {
        val optionValue = artooVariation.name.removePrefix(artooProduct.name).trim()
        return ShopifyProductVariant(
            optionValue,
            artooVariation.itemNumber,
            artooVariation.barcode!!,
            artooVariation.price,
            SelectedOption().apply {
                name = shopifyProduct.options[0].name
                value = optionValue
            }
        )
    }
}

private fun toSyncProduct(shopifyProduct: ShopifyProduct) =
    SyncProduct(
        shopifyId = shopifyProduct.id,
        vendor = shopifyProduct.vendor,
        type = shopifyProduct.productType,
        tags = (shopifyProduct.tags - listOf(shopifyProduct.vendor, shopifyProduct.productType)).toMutableSet(),
        synced = true
    )

private sealed class VariantBulkOperation {
    class Delete(val variant: ShopifyProductVariant) : VariantBulkOperation()
    class Create(val variant: ShopifyProductVariant) : VariantBulkOperation()
}
