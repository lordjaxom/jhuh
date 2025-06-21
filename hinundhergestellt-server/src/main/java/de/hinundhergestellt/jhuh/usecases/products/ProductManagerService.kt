package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategory
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.usecases.products.SyncProblem.Error
import de.hinundhergestellt.jhuh.usecases.products.SyncProblem.Warning
import de.hinundhergestellt.jhuh.usecases.products.VariantBulkOperation.Create
import de.hinundhergestellt.jhuh.usecases.products.VariantBulkOperation.Delete
import de.hinundhergestellt.jhuh.usecases.products.VariantBulkOperation.Update
import de.hinundhergestellt.jhuh.util.lazyWithReset
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedCategory
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.isDryRun
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KProperty1
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger {}

@Service
@VaadinSessionScope
class ProductManagerService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val shopifyProductMapper: ShopifyProductMapper,
    private val shopifyVariantMapper: ShopifyVariantMapper,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val syncVendorRepository: SyncVendorRepository
) : AutoCloseable {

    private val rootCategoriesLazy = lazyWithReset { artooDataStore.rootCategories.map { CategoryItem(it) } }
    val rootCategories by rootCategoriesLazy

    val vendors get(): List<SyncVendor> = syncVendorRepository.findAll()

    val refreshListeners by artooDataStore::refreshListeners

    init {
        refreshListeners += rootCategoriesLazy::reset
    }

    override fun close() {
        refreshListeners -= rootCategoriesLazy::reset
    }

    @Transactional
    fun updateItem(item: SyncableItem, vendor: SyncVendor?, replaceVendor: Boolean, type: String?, replaceType: Boolean, tags: String?) {
        val tagsAsSet = tags?.run { splitToSequence(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet() }
        when (item) {
            is CategoryItem -> {
                if (tagsAsSet != null) {
                    val syncCategory = item.syncCategory?.also { it.tags = tagsAsSet }
                        ?: SyncCategory(item.id, tagsAsSet).also { item.syncCategory = it }
                    syncCategoryRepository.save(syncCategory)
                }

                if (replaceVendor || replaceType) {
                    item.children.forEach { updateItem(it, vendor, replaceVendor, type, replaceType, null) }
                }
            }

            is ProductItem -> {
                var syncProduct = item.syncProduct
                if (syncProduct != null) {
                    if (replaceVendor) syncProduct.vendor = vendor
                    if (replaceType) syncProduct.type = type
                    if (tagsAsSet != null) syncProduct.tags = tagsAsSet
                } else {
                    syncProduct = SyncProduct(
                        artooId = item.id,
                        vendor = vendor,
                        type = type,
                        tags = tagsAsSet ?: mutableSetOf(),
                        synced = false
                    )
                    item.syncProduct = syncProduct
                }
                syncProductRepository.save(syncProduct)
            }
        }
    }

    @Transactional
    fun markForSync(product: ProductItem) {
        val syncProduct = product.syncProduct?.also { it.synced = true }
            ?: SyncProduct(artooId = product.id, synced = true).also { product.syncProduct = it }
        syncProductRepository.save(syncProduct)
    }

    @Transactional
    fun unmarkForSync(product: ProductItem) {
        product.syncProduct!!.also {
            it.synced = false
            syncProductRepository.save(it)
        }
    }

    @Transactional
    fun synchronize() {
        try {
            shopifyDataStore.products.forEach { reconcileFromShopify(it) }
            // TODO: Using rootCategories might save a lot of duplicate database loads and conditions (like description.ifEmpty { name })
            artooDataStore.findAllProducts().forEach { reconcileFromArtoo(it) }
            artooDataStore.rootCategories.forEach { reconcileCategories(it) }

            // TODO: Potentially deactivate products in Shopify when synced=false
            syncProductRepository.findAllBySyncedIsTrue().forEach { synchronizeWithShopify(it) }
        } catch (e: Exception) {
            logger.error(e) { "Synchronization failed" }
            throw e
        }
    }

    fun refresh() {
        artooDataStore.refresh()
        shopifyDataStore.refresh()
    }

    private fun checkSyncProblems(product: ArtooMappedProduct, syncProduct: SyncProduct?) = buildList {
        val barcodes = product.barcodes
        if (barcodes.isEmpty()) {
            add(Error("Produkt hat keine Barcodes"))
        } else if (barcodes.size < product.variations.size) {
            add(Warning("Nicht alle Variationen haben einen Barcode"))
        }
        syncProduct?.vendor.also {
            if (it == null) {
                add(Error("Produkt hat keinen Hersteller"))
            } else if (it.email == null || it.address == null) {
                add(Error("Herstellerangaben unvollständig"))
            }
        }
        if (syncProduct?.type == null) {
            add(Error("Produkt hat keine Produktart"))
        }
    }

    private fun reconcileFromShopify(shopifyProduct: ShopifyProduct) {
        // all products in Shopify are considered synced
        val syncProduct = syncProductRepository.findByShopifyId(shopifyProduct.id) ?: shopifyProduct.toSyncProduct()
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

        val tagsOfCategories = syncCategoryRepository.findByArtooIdIn(artooCategory.children.map { it.id }).asSequence().map { it.tags }
        val tagsOfProducts = artooCategory.products.asSequence().mapNotNull { syncProductRepository.findByArtooId(it.id)?.tags }
        val commonTags = (tagsOfCategories + tagsOfProducts).fold(null as Set<String>?) { acc, tags -> acc?.intersect(tags) ?: tags }
        if (commonTags.isNullOrEmpty()) {
            return
        }

        syncCategoryRepository.save(SyncCategory(artooCategory.id, commonTags.toMutableSet()))

        syncCategoryRepository.findByArtooIdIn(artooCategory.children.map { it.id }).forEach {
            it.tags.removeAll(commonTags)
            syncCategoryRepository.save(it)
        }
        artooCategory.products.asSequence()
            .mapNotNull { syncProductRepository.findByArtooId(it.id) }
            .onEach { it.tags.removeAll(commonTags) }
            .forEach { syncProductRepository.save(it) } // for later retrieval in same transaction
    }

    private fun synchronizeWithShopify(syncProduct: SyncProduct) {
        val artooProduct = syncProduct.artooId?.let { artooDataStore.findProductById(it) }
        var shopifyProduct = syncProduct.shopifyId?.let { shopifyDataStore.findProductById(it) }

        if (artooProduct != null && checkSyncProblems(artooProduct, syncProduct).has<Error>()) {
            logger.warn { "Product ${artooProduct.name} has errors, skipping synchronization" }
            return
        }

        if (artooProduct == null) {
            require(shopifyProduct != null) { "SyncProduct vanished from both ready2order and Shopify" }
            logger.info { "Product ${shopifyProduct!!.title} no longer in ready2order, delete from Shopify" }
            runBlocking { shopifyDataStore.delete(shopifyProduct!!) }
            syncProductRepository.delete(syncProduct)
            return
        }

        if (shopifyProduct == null) {
            logger.info { "Product ${artooProduct.name} only in ready2order, create in Shopify" }
            val unsavedShopifyProduct = shopifyProductMapper.mapToProduct(syncProduct, artooProduct)
            shopifyProduct = runBlocking { shopifyDataStore.create(unsavedShopifyProduct) }
            if (!shopifyProduct.isDryRun) {
                syncProduct.shopifyId = shopifyProduct.id
            }
        } else if (shopifyProductMapper.updateProduct(syncProduct, artooProduct, shopifyProduct)) {
            logger.info { "Product ${artooProduct.name} has changed, update in Shopify" }
            runBlocking { shopifyDataStore.update(shopifyProduct) }
        }

        val bulkOperations = syncProduct.variants
            .toList() // create copy to prevent concurrent modification when deleting variants
            .map { synchronizeWithShopify(it, artooProduct, shopifyProduct) }
        bulkOperations.allOf(Create::variant)?.let { runBlocking { shopifyDataStore.create(shopifyProduct, it) } }
        bulkOperations.allOf(Update::variant)?.let { runBlocking { shopifyDataStore.update(shopifyProduct, it) } }
        bulkOperations.allOf(Delete::variant)?.let { runBlocking { shopifyDataStore.delete(shopifyProduct, it) } }
    }

    private fun synchronizeWithShopify(syncVariant: SyncVariant, artooProduct: ArtooMappedProduct, shopifyProduct: ShopifyProduct)
            : VariantBulkOperation? {
        val artooVariation = artooProduct.findVariationByBarcode(syncVariant.barcode)
        val shopifyVariant = shopifyProduct.findVariantByBarcode(syncVariant.barcode)

        if (artooVariation == null) {
            require(shopifyVariant != null) { "SyncVariant vanished from both ready2order and Shopify" }
            logger.info { "Variant ${shopifyVariant.title} of ${shopifyProduct.title} no longer in ready2order, delete from Shopify" }
            syncVariant.product.variants.remove(syncVariant)
            return Delete(shopifyVariant)
        }

        if (shopifyVariant == null) {
            logger.info { "Variant ${artooVariation.name} only in ready2order, create in Shopify" }
            return Create(shopifyVariantMapper.mapToVariant(shopifyProduct, artooVariation))
        }

        if (shopifyVariantMapper.updateVariant(shopifyVariant, artooVariation)) {
            logger.info { "Variant ${artooVariation.name} has changed, update in Shopify" }
            return Update(shopifyVariant)
        }

        return null
    }

    inner class CategoryItem(val value: ArtooMappedCategory) : SyncableItem {

        internal var syncCategory = syncCategoryRepository.findByArtooId(value.id)

        val id by value::id

        val children = value.run { children.map { CategoryItem(it) } + products.map { ProductItem(it) } }

        override val itemId = "category-$id"
        override val name by value::name
        override val vendor = null
        override val type = null
        override val tagsAsSet get() = syncCategory?.tags?.toSet() ?: setOf()
        override val variations = null

        override fun filterBy(markedForSync: Boolean, withErrors: Boolean?, text: String) =
            children.any { it.filterBy(markedForSync, withErrors, text) }
    }

    inner class ProductItem(val value: ArtooMappedProduct) : SyncableItem {

        internal var syncProduct = syncProductRepository.findByArtooId(value.id)

        val id by value::id
        val isMarkedForSync get() = syncProduct?.synced ?: false

        val syncProblems get() = checkSyncProblems(value, syncProduct)

        override val itemId = "product-$id"
        override val name get() = value.description.ifEmpty { value.name }
        override val vendor get() = syncProduct?.vendor
        override val type get() = syncProduct?.type
        override val tagsAsSet get() = syncProduct?.tags?.toSet() ?: setOf()
        override val variations = if (value.hasOnlyDefaultVariant) 0 else value.variations.size

        override fun filterBy(markedForSync: Boolean, withErrors: Boolean?, text: String) =
            (!markedForSync || isMarkedForSync) &&
                    (withErrors == null || syncProblems.isNotEmpty() == withErrors) &&
                    (text.isEmpty() || name.contains(text, ignoreCase = true))
    }
}

sealed interface SyncableItem {

    val itemId: String
    val name: String
    val vendor: SyncVendor?
    val type: String?
    val tagsAsSet: Set<String>
    val variations: Int?

    val tags get() = tagsAsSet.sorted().joinToString(", ")

    fun filterBy(markedForSync: Boolean, withErrors: Boolean?, text: String): Boolean
}

sealed class SyncProblem(val message: String) {
    class Warning(message: String) : SyncProblem(message)
    class Error(message: String) : SyncProblem(message)

    override fun toString() = message
}

inline fun <reified T : SyncProblem> List<SyncProblem>.has() = any { it is T }

private sealed interface VariantBulkOperation {
    class Create(val variant: UnsavedShopifyProductVariant) : VariantBulkOperation
    class Update(val variant: ShopifyProductVariant) : VariantBulkOperation
    class Delete(val variant: ShopifyProductVariant) : VariantBulkOperation
}

private inline fun <reified T : VariantBulkOperation, V> List<VariantBulkOperation?>.allOf(property: KProperty1<T, V>) =
    filterIsInstance<T>().map { property.get(it) }.takeIf { it.isNotEmpty() }

@Suppress("KotlinUnreachableCode")
private fun ShopifyProduct.toSyncProduct() =
    SyncProduct(
        shopifyId = id,
        vendor = throw NotImplementedError("vendor from ShopifyProduct"),
        type = productType,
        tags = (tags - listOf(vendor, productType)).toMutableSet(),
        synced = true
    )
