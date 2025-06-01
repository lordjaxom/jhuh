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
import de.hinundhergestellt.jhuh.util.lazyWithReset
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductVariantOption
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
    val rootCategories = artooDataStore.rootCategories.map { Category(it) }

    @Transactional
    fun updateItem(item: SyncableItem, vendor: String?, type: String?, tags: String) {
        val tagsAsSet = tags.splitToSequence(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        when (item) {
            is Category -> {
                val syncCategory = item.syncCategory
                    ?.also { it.tags = tagsAsSet }
                    ?: tagsAsSet.takeIf { it.isNotEmpty() }?.let { SyncCategory(item.id, it) }
                syncCategory?.also { syncCategoryRepository.save(it) }

                if (vendor != null || type != null) {
                    item.value.findAllProducts().forEach { artooProduct ->
                        val syncProduct = syncProductRepository.findByArtooId(artooProduct.id)
                            ?.also { if (vendor != null) it.vendor = vendor; if (type != null) it.type = type }
                            ?: SyncProduct(artooId = artooProduct.id, vendor = vendor, type = type, synced = false)
                        syncProductRepository.save(syncProduct)
                    }
                }
            }

            is Product -> {
                val syncProduct = item.syncProduct
                    ?.also { it.vendor = vendor; it.type = type; it.tags = tagsAsSet }
                    ?: SyncProduct(artooId = item.id, vendor = vendor, type = type, tags = tagsAsSet, synced = false)
                syncProductRepository.save(syncProduct)
            }
        }
        item.reset()
    }

    @Transactional
    fun markForSync(product: Product) {
        val syncProduct = product.syncProduct
            ?.apply { synced = true }
            ?: run { SyncProduct(artooId = product.id, synced = true) }
        syncProductRepository.save(syncProduct)
        product.reset()
    }

    @Transactional
    fun unmarkForSync(product: Product) {
        product.syncProduct?.also {
            it.synced = false
            syncProductRepository.save(it)
            product.reset()
        }
    }

    @Transactional
    fun synchronize() {
        try {
            shopifyDataStore.products.forEach { reconcileFromShopify(it) }
            artooDataStore.findAllProducts().forEach { reconcileFromArtoo(it) }
            artooDataStore.rootCategories.forEach { reconcileCategories(it) }

            syncProductRepository.findAllBy().forEach { synchronizeWithShopify(it) }

            rootCategories.forEach { it.reset() }
        } catch (e: Exception) {
            logger.error(e) { "Synchronization failed" }
            throw e
        }
    }

    private fun checkSyncProblems(product: ArtooMappedProduct, syncProduct: SyncProduct?) = buildList {
        val barcodes = product.barcodes
        if (barcodes.isEmpty()) {
            add(SyncProblem.Error("Produkt hat keine Barcodes"))
        } else if (barcodes.size < product.variations.size) {
            add(SyncProblem.Warning("Nicht alle Variationen haben einen Barcode"))
        }
        if (syncProduct?.vendor == null) {
            add(SyncProblem.Error("Produkt hat keinen Hersteller"))
        }
        if (syncProduct?.type == null) {
            add(SyncProblem.Error("Produkt hat keine Produktart"))
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

        if (artooProduct != null && checkSyncProblems(artooProduct, syncProduct).has<SyncProblem.Error>()) {
            logger.warn { "Product ${artooProduct.name} has errors, skipping synchronization" }
            return
        }

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
            else -> listOf(
                ShopifyProductOption(
                    "Farbe",
                    artooProduct.variations.map { it.name.removePrefix(artooProduct.name).trim() }
                )
            )
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
            artooVariation.itemNumber ?: "",
            artooVariation.barcode!!,
            artooVariation.price,
            listOf(ShopifyProductVariantOption(
                shopifyProduct.options[0].name,
                optionValue
            ))
        )
    }

    inner class Category(val value: ArtooMappedCategory) : SyncableItem {

        private val lazySyncCategory = lazyWithReset { syncCategoryRepository.findByArtooId(value.id) }
        internal val syncCategory by lazySyncCategory

        val id by value::id

        val childrenAndProducts = value.run { children.map { Category(it) } + products.map { Product(it) } }

        override val name by value::name
        override val vendor = null
        override val type = null
        override val tagsAsSet get() = syncCategory?.tags?.toSet() ?: setOf()
        override val variations = null

        override fun filterBy(markedForSync: Boolean, withErrors: Boolean?, text: String) =
            childrenAndProducts.any { it.filterBy(markedForSync, withErrors, text) }

        override fun reset() {
            lazySyncCategory.reset()
            childrenAndProducts.forEach { it.reset() }
        }
    }

    inner class Product(val value: ArtooMappedProduct) : SyncableItem {

        private val lazySyncProduct = lazyWithReset { syncProductRepository.findByArtooId(value.id) }
        internal val syncProduct by lazySyncProduct

        val id by value::id
        val isMarkedForSync get() = syncProduct?.synced ?: false

        private val lazySyncProblems = lazyWithReset { checkSyncProblems(value, syncProduct) }
        val syncProblems by lazySyncProblems

        override val name by value::name
        override val vendor get() = syncProduct?.vendor
        override val type get() = syncProduct?.type
        override val tagsAsSet get() = syncProduct?.tags?.toSet() ?: setOf()
        override val variations = when (value) {
            is SingleArtooMappedProduct -> 0
            else -> value.variations.size
        }

        override fun filterBy(markedForSync: Boolean, withErrors: Boolean?, text: String) =
            (!markedForSync || isMarkedForSync) &&
                    (withErrors == null || syncProblems.isNotEmpty() == withErrors) &&
                    (text.isEmpty() || name.contains(text, ignoreCase = true))

        override fun reset() {
            lazySyncProblems.reset()
            lazySyncProduct.reset()
        }
    }
}

sealed interface SyncableItem {

    val name: String
    val vendor: String?
    val type: String?
    val tagsAsSet: Set<String>
    val variations: Int?

    val tags get() = tagsAsSet.sorted().joinToString(", ")

    fun filterBy(markedForSync: Boolean, withErrors: Boolean?, text: String): Boolean

    fun reset()
}

sealed class SyncProblem(val message: String) {
    class Warning(message: String) : SyncProblem(message)
    class Error(message: String) : SyncProblem(message)

    override fun toString() = message
}

inline fun <reified T> List<SyncProblem>.has() = any { it is T }

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
