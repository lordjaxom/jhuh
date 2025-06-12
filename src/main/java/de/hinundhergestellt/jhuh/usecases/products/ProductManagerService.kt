package de.hinundhergestellt.jhuh.usecases.products

import arrow.core.Option
import com.shopify.admin.types.ProductStatus
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
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldType
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.containsId
import de.hinundhergestellt.jhuh.vendors.shopify.client.findById
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.isDryRun
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.reflect.KMutableProperty0
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger {}

private val INVALID_TAG_CHARACTERS = """[^A-ZÄÖÜa-zäöüß0-9\\._ -]""".toRegex()

@Service
@VaadinSessionScope
class ProductManagerService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val syncVendorRepository: SyncVendorRepository
) {
    // TODO: Better to add fetchChildren here and create SyncableItems on the fly? But refreshItem in TreeDataProvider doesn't reload the
    // TODO: item, so clearing the database objects would still be required
    private val lazyRootCategories = lazyWithReset { artooDataStore.rootCategories.map { CategoryItem(it) } }
    val rootCategories by lazyRootCategories

    val vendors get(): List<SyncVendor> = syncVendorRepository.findAll()

    val stateChangeListeners by artooDataStore::stateChangeListeners

    @Transactional
    fun updateItem(item: SyncableItem, vendor: Option<SyncVendor>?, type: String?, tags: String) {
        val tagsAsSet = tags.splitToSequence(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        when (item) {
            is CategoryItem -> {
                val syncCategory = item.syncCategory
                    ?.also { it.tags = tagsAsSet }
                    ?: tagsAsSet.takeIf { it.isNotEmpty() }?.let { SyncCategory(item.id, it) }
                syncCategory?.also { syncCategoryRepository.save(it) }

                if (vendor != null || type != null) {
                    item.value.findAllProducts().forEach { product ->
                        var syncProduct = syncProductRepository.findByArtooId(product.id)
                        if (syncProduct != null) {
                            if (vendor != null) syncProduct.vendor = vendor.getOrNull()
                            if (type != null) syncProduct.type = type.ifEmpty { null }
                        } else {
                            syncProduct = SyncProduct(artooId = product.id, vendor = vendor?.getOrNull(), type = type, synced = false)
                        }
                        syncProductRepository.save(syncProduct)
                    }
                }
            }

            is ProductItem -> {
                val syncProduct = item.syncProduct
                    ?.also { it.vendor = vendor!!.getOrNull(); it.type = type; it.tags = tagsAsSet }
                    ?: SyncProduct(artooId = item.id, vendor = vendor!!.getOrNull(), type = type, tags = tagsAsSet, synced = false)
                syncProductRepository.save(syncProduct)
            }
        }
        item.reset()
    }

    @Transactional
    fun markForSync(product: ProductItem) {
        val syncProduct = product.syncProduct
            ?.apply { synced = true }
            ?: SyncProduct(artooId = product.id, synced = true)
        syncProductRepository.save(syncProduct)
        product.reset()
    }

    @Transactional
    fun unmarkForSync(product: ProductItem) {
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
            // TODO: Using rootCategories might save a lot of duplicate database loads and conditions (like description.ifEmpty { name })
            artooDataStore.findAllProducts().forEach { reconcileFromArtoo(it) }
            artooDataStore.rootCategories.forEach { reconcileCategories(it) }

            // TODO: Potentially deactivate products in Shopify when synced=false
            syncProductRepository.findAllBySyncedIsTrue().forEach { synchronizeWithShopify(it) }

            lazyRootCategories.reset()
        } catch (e: Exception) {
            logger.error(e) { "Synchronization failed" }
            throw e
        }
    }

    fun refreshItems() {
        artooDataStore.refresh()
        // TODO: Better run this when stateUpdateListener is invoked? Not if synchronization uses rootCategories! But when ArtooDataStore
        // TODO: does automatic refresh, so probably both
        lazyRootCategories.reset()
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
            shopifyDataStore.delete(shopifyProduct)
            syncProductRepository.delete(syncProduct)
            return
        }

        if (shopifyProduct == null) {
            logger.info { "Product ${artooProduct.name} only in ready2order, create in Shopify" }
            val unsavedShopifyProduct = buildShopifyProduct(syncProduct, artooProduct)
            shopifyProduct = shopifyDataStore.create(unsavedShopifyProduct)
            if (!shopifyProduct.isDryRun) {
                syncProduct.shopifyId = shopifyProduct.id
            }
        } else if (updateShopifyProduct(syncProduct, shopifyProduct, artooProduct)) {
            logger.info { "Product ${artooProduct.name} has changed, update in Shopify" }
            shopifyDataStore.update(shopifyProduct)
        }

        val bulkOperations = syncProduct.variants
            .toList() // create copy to prevent concurrent modification when deleting variant
            .map { synchronizeWithShopify(it, artooProduct, shopifyProduct) }
        bulkOperations.allOf<Create>()?.run { shopifyDataStore.create(shopifyProduct, map { it.variant }) }
        bulkOperations.allOf<Update>()?.run { shopifyDataStore.update(shopifyProduct, map { it.variant }) }
        bulkOperations.allOf<Delete>()?.run { shopifyDataStore.delete(shopifyProduct, map { it.variant }) }
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
            val unsavedShopifyVariant = buildShopifyVariant(shopifyProduct, artooVariation)
            return Create(unsavedShopifyVariant)
        } else if (updateShopifyVariant(shopifyVariant, artooVariation)) {
            logger.info { "Variant ${artooVariation.name} has changed, update in Shopify" }
            return Update(shopifyVariant)
        }

        return null
    }

    private fun buildShopifyProduct(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct) =
        UnsavedShopifyProduct(
            artooProduct.description.ifEmpty { artooProduct.name },
            syncProduct.vendor!!.name,
            syncProduct.type!!,
            ProductStatus.DRAFT,
            buildTags(syncProduct, artooProduct),
            buildShopifyProductOptions(artooProduct),
            buildShopifyMetafields(syncProduct)
        )

    private fun buildShopifyProductOptions(artooProduct: ArtooMappedProduct) =
        if (!artooProduct.hasOnlyDefaultVariant) listOf(UnsavedShopifyProductOption("Farbe", artooProduct.variations.map { it.name }))
        else listOf()

    private fun updateShopifyProduct(syncProduct: SyncProduct, shopifyProduct: ShopifyProduct, artooProduct: ArtooMappedProduct): Boolean {
        require(shopifyProduct.hasOnlyDefaultVariant == artooProduct.hasOnlyDefaultVariant) { "Switching variants and standalone not supported yet" }
        return updateProperty(shopifyProduct::title, artooProduct.description.ifEmpty { artooProduct.name }) or
                updateProperty(shopifyProduct::vendor, syncProduct.vendor!!.name) or
                updateProperty(shopifyProduct::productType, syncProduct.type!!) or
                updateProperty(shopifyProduct::tags, buildTags(syncProduct, artooProduct)) or
                updateShopifyMetafields(syncProduct, shopifyProduct)
    }

    private fun buildShopifyMetafields(syncProduct: SyncProduct) =
        listOf(
            metafield("vendor_address", syncProduct.vendor!!.address!!, ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD),
            metafield("vendor_email", syncProduct.vendor!!.email!!, ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD),
        )

    private fun updateShopifyMetafields(syncProduct: SyncProduct, shopifyProduct: ShopifyProduct): Boolean {
        val metafields = buildShopifyMetafields(syncProduct)
        return shopifyProduct.metafields.removeAll { !metafields.containsId(it) } or
                shopifyProduct.metafields.addAll(metafields.filter { !shopifyProduct.metafields.containsId(it) }) or
                shopifyProduct.metafields.asSequence()
                    .map { it to metafields.findById(it)!! }
                    .map { (old, new) -> updateProperty(old::value, new.value) or updateProperty(old::type, new.type) }
                    .toList() // enforce terminal operation
                    .any { it }
    }

    private fun buildShopifyVariant(shopifyProduct: ShopifyProduct, artooVariation: ArtooMappedVariation) =
        UnsavedShopifyProductVariant(
            artooVariation.itemNumber ?: "",
            artooVariation.barcode!!,
            artooVariation.price,
            buildShopifyVariantOptions(shopifyProduct, artooVariation)
        )

    private fun updateShopifyVariant(shopifyVariant: ShopifyProductVariant, artooVariation: ArtooMappedVariation) =
        // @formatter:off
        updateShopifyVariantOptions(shopifyVariant, artooVariation) or
        updateProperty(shopifyVariant::sku, artooVariation.itemNumber ?: "") or
        updateProperty(shopifyVariant::price, artooVariation.price)
        // @formatter:on

    private fun buildShopifyVariantOptions(shopifyProduct: ShopifyProduct, artooVariation: ArtooMappedVariation) =
        if (!shopifyProduct.hasOnlyDefaultVariant) listOf(ShopifyProductVariantOption(shopifyProduct.options[0].name, artooVariation.name))
        else listOf()

    private fun updateShopifyVariantOptions(shopifyVariant: ShopifyProductVariant, artooVariation: ArtooMappedVariation) =
        if (!artooVariation.isDefaultVariant) updateProperty(shopifyVariant.options[0]::value, artooVariation.name)
        else false

    private fun <T> updateProperty(property: KMutableProperty0<T>, value: T): Boolean {
        if (property.get() != value) {
            logger.info { "Property ${property.name} changed from ${property.get()} to $value" }
            property.set(value)
            return true
        }
        return false
    }

    private fun buildTags(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct): Set<String> {
        val tags = buildList {
            val categoryIds = artooDataStore.findCategoriesByProduct(artooProduct).map { it.id }.toList()
            addAll(syncCategoryRepository.findByArtooIdIn(categoryIds).asSequence().flatMap { it.tags })
            addAll(syncProduct.tags)
            add(syncProduct.vendor!!.name)
            add(syncProduct.type!!)
        }
        return tags.map { it.replace(INVALID_TAG_CHARACTERS, "") }.toSet()
    }

    inner class CategoryItem(val value: ArtooMappedCategory) : SyncableItem {

        // TODO: lazy probably unnecessary when just resetting lazyRootCategories above
        private val lazySyncCategory = lazyWithReset { syncCategoryRepository.findByArtooId(value.id) }
        internal val syncCategory by lazySyncCategory

        val id by value::id

        val childrenAndProducts = value.run { children.map { CategoryItem(it) } + products.map { ProductItem(it) } }

        override val itemId = "category-$id"
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

    inner class ProductItem(val value: ArtooMappedProduct) : SyncableItem {

        // TODO: lazy probably unnecessary when just resetting lazyRootCategories above
        private val lazySyncProduct = lazyWithReset { syncProductRepository.findByArtooId(value.id) }
        internal val syncProduct by lazySyncProduct

        val id by value::id
        val isMarkedForSync get() = syncProduct?.synced ?: false

        private val lazySyncProblems = lazyWithReset { checkSyncProblems(value, syncProduct) }
        val syncProblems by lazySyncProblems

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

        override fun reset() {
            lazySyncProblems.reset()
            lazySyncProduct.reset()
        }
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

    fun reset()
}

sealed class SyncProblem(val message: String) {
    class Warning(message: String) : SyncProblem(message)
    class Error(message: String) : SyncProblem(message)

    override fun toString() = message
}

inline fun <reified T : SyncProblem> List<SyncProblem>.has() = any { it is T }

private sealed class VariantBulkOperation {
    class Create(val variant: UnsavedShopifyProductVariant) : VariantBulkOperation()
    class Update(val variant: ShopifyProductVariant) : VariantBulkOperation()
    class Delete(val variant: ShopifyProductVariant) : VariantBulkOperation()
}

private inline fun <reified T> List<VariantBulkOperation?>.allOf(): List<T>? {
    return filterIsInstance<T>().takeIf { it.isNotEmpty() }
}

@Suppress("KotlinUnreachableCode")
private fun ShopifyProduct.toSyncProduct() =
    SyncProduct(
        shopifyId = id,
        vendor = throw NotImplementedError("vendor from ShopifyProduct"),
        type = productType,
        tags = (tags - listOf(vendor, productType)).toMutableSet(),
        synced = true
    )

fun metafield(key: String, value: String, type: ShopifyMetafieldType) = ShopifyMetafield("hinundhergestellt", key, value, type)