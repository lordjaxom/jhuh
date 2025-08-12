package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.barcodes.BarcodeGenerator
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.components.Article
import de.hinundhergestellt.jhuh.core.lazyWithReset
import de.hinundhergestellt.jhuh.usecases.labels.LabelGeneratorService
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedCategory
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.suspendCancellableCoroutine
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionOperations
import kotlin.coroutines.resume

private val logger = KotlinLogging.logger {}

@Service
@VaadinSessionScope
class ProductManagerService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val syncVendorRepository: SyncVendorRepository,
    private val transactionOperations: TransactionOperations,
    private val barcodeGenerator: BarcodeGenerator,
    private val labelGeneratorService: LabelGeneratorService,
) : AutoCloseable {

    private val rootCategoriesLazy = lazyWithReset { artooDataStore.rootCategories.map { CategoryTreeItem(it) } }
    val rootCategories by rootCategoriesLazy

    val vendors get(): List<SyncVendor> = syncVendorRepository.findAll()

    val refreshListeners by artooDataStore::refreshListeners

    init {
        refreshListeners += rootCategoriesLazy::reset
    }

    override fun close() {
        refreshListeners -= rootCategoriesLazy::reset
    }

    suspend fun refresh() =
        suspendCancellableCoroutine {
            fun handler() {
                refreshListeners -= ::handler
                it.resume(Unit)
            }
            it.invokeOnCancellation { refreshListeners -= ::handler }
            refreshListeners += ::handler
            artooDataStore.refresh()
        }

    suspend fun update(artooProduct: ArtooMappedProduct?, syncProduct: SyncProduct?) {
        if (artooProduct != null) artooDataStore.update(artooProduct)
        if (syncProduct != null) transactionOperations.execute { syncProductRepository.save(syncProduct) }
    }

    suspend fun update(artooVariation: ArtooMappedVariation?, syncVariant: SyncVariant?) {
        if (artooVariation != null) artooDataStore.update(artooVariation)
        if (syncVariant != null) transactionOperations.execute { syncVariantRepository.save(syncVariant) }
    }

    suspend fun generateNewBarcodes(product: ProductTreeItem, report: suspend (String) -> Unit) {
        report("Shopify-Produktkatalog aktualisieren...")
        shopifyDataStore.withLockAndRefresh {

            report("Barcodes für ${product.name} generieren...")

            val shopifyProduct = product.syncProduct.shopifyId?.let { shopifyDataStore.findProductById(it) }

            val syncVariantsToUpdate = mutableListOf<SyncVariant>()
            val shopifyVariantsToUpdate = mutableListOf<ShopifyProductVariant>()
            product.value.variations.forEach {
                generateNewBarcode(it, shopifyProduct, syncVariantsToUpdate, shopifyVariantsToUpdate)
                labelGeneratorService.createLabel(Article(product.value, it), it.stockValue.toInt())
            }

            artooDataStore.update(product.value)
            if (shopifyProduct != null && shopifyVariantsToUpdate.isNotEmpty()) {
                shopifyDataStore.update(shopifyProduct, shopifyVariantsToUpdate)
            }
            if (syncVariantsToUpdate.isNotEmpty()) {
                transactionOperations.execute { syncVariantRepository.saveAll(syncVariantsToUpdate) }
            }
        }
    }

    private fun generateNewBarcode(
        variation: ArtooMappedVariation,
        shopifyProduct: ShopifyProduct?,
        syncVariantsToUpdate: MutableList<SyncVariant>,
        shopifyVariantsToUpdate: MutableList<ShopifyProductVariant>
    ) {
        val oldBarcode = variation.barcode
        val newBarcode = barcodeGenerator.generate()
        variation.product.barcode = newBarcode

        if (oldBarcode == null) return

        syncVariantRepository.findByBarcode(oldBarcode)?.also {
            it.barcode = newBarcode
            syncVariantsToUpdate.add(it)
        }
        shopifyProduct?.findVariantByBarcode(oldBarcode)?.also {
            it.barcode = newBarcode
            shopifyVariantsToUpdate.add(it)
        }
    }

    inner class CategoryTreeItem(val value: ArtooMappedCategory) : TreeItem {

        internal var syncCategory = syncCategoryRepository.findByArtooId(value.id)

        val id by value::id

        override val itemId = "category-$id"
        override val name by value::name
        override val vendor = null
        override val type = null
        override val tagsAsSet get() = syncCategory?.tags?.toSet() ?: setOf()
        override val variations = null
        override val hasChildren = true
        override val children = value.run { children.map { CategoryTreeItem(it) } + products.map { ProductTreeItem(it) } }

        override fun filterBy(markedForSync: Boolean, text: String) =
            children.any { it.filterBy(markedForSync, text) }
    }

    inner class ProductTreeItem(val value: ArtooMappedProduct) : TreeItem {

        internal val syncProduct = syncProductRepository.findByArtooId(value.id) ?: value.toSyncProduct()

        val id by value::id
        val isMarkedForSync get() = syncProduct.synced

        override val itemId = id
        override val name get() = value.description.ifEmpty { value.name }
        override val vendor get() = syncProduct.vendor
        override val type get() = syncProduct.type
        override val tagsAsSet get() = syncProduct.tags.toSet()
        override val variations = if (value.hasOnlyDefaultVariant) 0 else value.variations.size
        override val hasChildren = !value.hasOnlyDefaultVariant
        override val children = value.variations.map { VariationTreeItem(it) }

        override fun filterBy(markedForSync: Boolean, text: String) =
            (!markedForSync || isMarkedForSync) &&
                    (text.isEmpty() || name.contains(text, ignoreCase = true))
    }

    inner class VariationTreeItem(val value: ArtooMappedVariation) : TreeItem {

        internal var syncVariant = syncVariantRepository.findByArtooId(value.id)

        val id by value::id

        override val itemId = "variation-$id"
        override val name by value::name
        override val vendor = null
        override val type = null
        override val tagsAsSet = setOf<String>()
        override val variations = null
        override val hasChildren = false
        override val children = listOf<TreeItem>()

        override fun filterBy(markedForSync: Boolean, text: String) = true
    }
}

sealed interface TreeItem {

    val itemId: String
    val name: String
    val vendor: SyncVendor?
    val type: String?
    val tagsAsSet: Set<String>
    val variations: Int?
    val hasChildren: Boolean
    val children: List<TreeItem>

    val tags get() = tagsAsSet.sorted().joinToString(", ")

    fun filterBy(markedForSync: Boolean, text: String): Boolean
}

private fun ArtooMappedProduct.toSyncProduct() =
    SyncProduct(artooId = id).apply {
        variants += variations
            .filter { it.barcode != null }
            .map { it.toSyncVariant(this) }
    }

private fun ArtooMappedVariation.toSyncVariant(product: SyncProduct) =
    SyncVariant(product = product, barcode = barcode!!, artooId = id)