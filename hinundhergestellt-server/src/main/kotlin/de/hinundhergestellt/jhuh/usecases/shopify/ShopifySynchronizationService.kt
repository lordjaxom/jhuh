package de.hinundhergestellt.jhuh.usecases.shopify

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.barcodes.BarcodeGenerator
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.mapping.toQuotedString
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.usecases.labels.LabelGeneratorService
import de.hinundhergestellt.jhuh.usecases.shopify.VariantBulkOperation.Create
import de.hinundhergestellt.jhuh.usecases.shopify.VariantBulkOperation.Delete
import de.hinundhergestellt.jhuh.usecases.shopify.VariantBulkOperation.Update
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.findById
import de.hinundhergestellt.jhuh.vendors.shopify.client.isDryRun
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty1

private val logger = KotlinLogging.logger {}

private val PROPERTY_TO_FIELD = mapOf(
    "title" to Pair("Titel", true),
    "vendor" to Pair("Hersteller", true),
    "productType" to Pair("Produktart", true),
    "descriptionHtml" to Pair("Beschreibung", false),
    "tags" to Pair("Tags", true),
    "vendor_email" to Pair("Hersteller-Email", true),
    "vendor_address" to Pair("Herstelleradresse", false),
    "product_specs" to Pair("Produktspezifikationen", false)
)

@Service
@VaadinSessionScope
class ShopifySynchronizationService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val shopifyProductMapper: ShopifyProductMapper,
    private val shopifyVariantMapper: ShopifyVariantMapper,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val syncVendorRepository: SyncVendorRepository,
    private val barcodeGenerator: BarcodeGenerator,
    private val labelGeneratorService: LabelGeneratorService,
    private val shopTexterService: ShopTexterService,
    private val mappingService: MappingService,
    private val transactionOperations: TransactionOperations,
) {
    val items = mutableListOf<Item>()

    suspend fun refresh(report: suspend (String) -> Unit) {
        report("Aktualisiere Shopify- und ready2order-Produktkataloge...")
        coroutineScope {
            val job = async { shopifyDataStore.refreshAndAwait() }
            artooDataStore.refreshAndAwait()
            job.await()
        }

        // TODO: Missing SyncVariants for variations new in ready2order (would report mapping error anyway, necessary?)

        report("Gleiche synchronisierte Produkte mit Shopify ab...")
        items.clear()
        syncProductRepository.findAllBySyncedIsTrue().forEach { synchronize(it) }
    }

    suspend fun apply(items: Set<Item>, report: suspend (String) -> Unit) {
        report("Übernehme markierte Änderungen nach Shopify...")

        val changedProducts = linkedSetOf<ShopifyProduct>()
        items.forEach { item ->
            when (item) {
                is ImmediateItem -> item.block()
                is DeferredProductItem -> changedProducts.add(item.apply { block() }.product)
            }
        }
        changedProducts.forEach { shopifyDataStore.update(it) }
    }

    private suspend fun synchronize(syncProduct: SyncProduct) {
        val artooProduct = syncProduct.artooId?.let { artooDataStore.findProductById(it) }
        val shopifyProduct = syncProduct.shopifyId?.let { shopifyDataStore.findProductById(it) }

        logger.info { "Sync ${syncProduct.id} ${artooProduct?.name} ${shopifyProduct?.title}" }

        if (artooProduct != null && mappingService.checkForProblems(artooProduct, syncProduct).isNotEmpty()) {
            logger.warn { "Product ${artooProduct.name} has problems, skip synchronization" }
            return
        }

        if (artooProduct == null) {
            require(shopifyProduct != null) { "SyncProduct vanished from both ready2order and Shopify" }
            prepareDeleteProduct(syncProduct, shopifyProduct)
            return
        }

        if (shopifyProduct == null) {
            prepareCreateProduct(syncProduct, artooProduct)
            return
        }

        if (shopifyProduct.options.any { it.linkedMetafield != null }) {
            logger.warn { "Product ${shopifyProduct.title} has options with linked metafield, skip synchronization" }
            return
        }

        require(shopifyProduct.hasOnlyDefaultVariant == artooProduct.hasOnlyDefaultVariant) { "Switching variants and standalone not supported yet" }

        prepareUpdateProductProperty(shopifyProduct, shopifyProduct::title, artooProduct.description)
        prepareUpdateProductProperty(shopifyProduct, shopifyProduct::vendor, syncProduct.vendor!!.name)
        prepareUpdateProductProperty(shopifyProduct, shopifyProduct::productType, syncProduct.type!!)
        prepareUpdateProductProperty(shopifyProduct, shopifyProduct::descriptionHtml, syncProduct.descriptionHtml ?: "")
        prepareUpdateProductProperty(shopifyProduct, shopifyProduct::tags, mappingService.allTags(syncProduct, artooProduct))

        mappingService.customMetafields(syncProduct).forEach { prepareUpdateProductMetafield(shopifyProduct, it) }

//
//        if (shopifyProductMapper.updateProduct(syncProduct, artooProduct, shopifyProduct)) {
//            logger.info { "Product ${artooProduct.name} has changed, update in Shopify" }
//            shopifyProduct.dirtyTracker.fields.forEach {
//                items += ProductItem(shopifyProduct.title, "Property $it wurde geändert") {}
//            }
//            runBlocking { shopifyDataStore.update(shopifyProduct) }
//            shopTexterService.updateProduct(syncProduct.id, shopifyProduct)
//        }
//
//        val bulkOperations = syncProduct.variants
//            .toList() // create copy to prevent concurrent modification when deleting variants
//            .map { synchronizeWithShopify(it, artooProduct, shopifyProduct) }
//        bulkOperations.allOf(Create::variant)?.let { runBlocking { shopifyDataStore.create(shopifyProduct, it) } }
//        bulkOperations.allOf(Update::variant)?.let { runBlocking { shopifyDataStore.update(shopifyProduct, it) } }
//        bulkOperations.allOf(Delete::variant)?.let { runBlocking { shopifyDataStore.delete(shopifyProduct, it) } }
    }

    private fun synchronizeWithShopify(syncVariant: SyncVariant, artooProduct: ArtooMappedProduct, shopifyProduct: ShopifyProduct)
            : VariantBulkOperation? {
        val artooVariation = artooProduct.findVariationByBarcode(syncVariant.barcode)
        val shopifyVariant = shopifyProduct.findVariantByBarcode(syncVariant.barcode)

        if (artooVariation == null && shopifyVariant == null) {
            logger.info { "Variant of ${artooProduct.name} with barcode ${syncVariant.barcode} vanished, forget" }
            syncVariant.product.variants.remove(syncVariant)
            return null
        }

        if (artooVariation == null) {
            require(shopifyVariant != null) // already covered by previous condition
            logger.info { "Variant ${shopifyVariant.title} of ${shopifyProduct.title} no longer in ready2order, delete from Shopify" }
            syncVariant.product.variants.remove(syncVariant)
            return Delete(shopifyVariant)
        }

        if (!artooProduct.hasOnlyDefaultVariant && artooVariation.name.isEmpty()) {
            logger.warn { "Variant of ${artooProduct.name} with barcode ${artooVariation.barcode} has no name, skip synchronization" }
            return null
        }

        if (shopifyVariant == null) {
            logger.info { "Variant ${artooVariation.name} of ${artooProduct.name} only in ready2order, create in Shopify" }
            return Create(
                shopifyVariantMapper.mapToVariant(artooProduct, syncVariant, artooVariation)
            )
        }

        if (shopifyVariantMapper.updateVariant(shopifyVariant, artooVariation)) {
            logger.info { "Variant ${artooVariation.name} of ${artooProduct.name} has changed, update in Shopify" }
            return Update(shopifyVariant)
        }

        return null
    }

    private fun prepareDeleteProduct(syncProduct: SyncProduct, shopifyProduct: ShopifyProduct) {
        items += ImmediateItem(Type.PRODUCT, shopifyProduct.title, "Produkt wird in Shopify gelöscht") {
            shopifyDataStore.delete(shopifyProduct)
            shopTexterService.removeProduct(syncProduct.id)
            transactionOperations.execute { syncProductRepository.delete(syncProduct) }
        }
    }

    private fun prepareCreateProduct(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct) {
        val unsavedShopifyProduct = shopifyProductMapper.mapToProduct(syncProduct, artooProduct)
        val unsavedVariants = syncProduct.variants
            .mapNotNull { variant -> variant.artooId?.let { artooProduct.findVariationById(it) }?.let { variant to it } }
            .map { (sync, artoo) -> sync to shopifyVariantMapper.mapToVariant(artooProduct, sync, artoo) }
        val variantsText = if (!artooProduct.hasOnlyDefaultVariant) " mit ${unsavedVariants.size} Varianten" else ""

        items += ImmediateItem(Type.PRODUCT, unsavedShopifyProduct.title, "Produkt wird${variantsText} in Shopify neu erstellt") {
            val savedShopifyProduct = shopifyDataStore.create(unsavedShopifyProduct)
            if (!savedShopifyProduct.isDryRun) syncProduct.shopifyId = savedShopifyProduct.id
            shopifyDataStore.create(savedShopifyProduct, unsavedVariants.map { it.second })
            if (!savedShopifyProduct.isDryRun)
                unsavedVariants.forEachIndexed { index, (sync, _) -> sync.shopifyId = savedShopifyProduct.variants[index].id }
            shopTexterService.updateProduct(syncProduct.id, savedShopifyProduct)
            transactionOperations.execute { syncProductRepository.save(syncProduct) }
        }
    }

    private fun <T> prepareUpdateProductProperty(
        shopifyProduct: ShopifyProduct,
        property: KMutableProperty0<T>,
        newValue: T
    ) {
        if (property.get() == newValue) return

        val field = PROPERTY_TO_FIELD[property.name] ?: Pair("Property ${property.name}", false)
        val change = if (field.second) " von ${property.get().toQuotedString()} auf ${newValue.toQuotedString()}" else ""
        val message = "${field.first}$change geändert"
        items += DeferredProductItem(shopifyProduct, message) { property.set(newValue) }
    }

    private fun prepareUpdateProductMetafield(
        shopifyProduct: ShopifyProduct,
        newMetafield: ShopifyMetafield
    ) {
        val oldMetafield = shopifyProduct.metafields.findById(newMetafield)
        val field = PROPERTY_TO_FIELD[newMetafield.key] ?: Pair(newMetafield.key, false)
        if (oldMetafield == null) {
            val change = if (field.second) " ${newMetafield.value.toQuotedString()}" else ""
            val message = "${field.first}$change hinzugefügt"
            items += DeferredProductItem(shopifyProduct, message) { shopifyProduct.metafields.add(newMetafield) }
        } else if (oldMetafield.value != newMetafield.value) {
            val change = if (field.second) " von ${oldMetafield.value.toQuotedString()} auf ${newMetafield.value.toQuotedString()}" else ""
            val message = "${field.first}$change geändert"
            items += DeferredProductItem(shopifyProduct, message) { oldMetafield.value = newMetafield.value }
        }
    }

    enum class Type {
        PRODUCT, VARIANT
    }

    sealed interface Item {
        val type: Type
        val title: String
        val message: String
    }

    private inner class ImmediateItem(
        override val type: Type,
        override val title: String,
        override val message: String,
        val block: suspend () -> Unit
    ) : Item

    private inner class DeferredProductItem(
        val product: ShopifyProduct,
        override val message: String,
        val block: () -> Unit,
    ) : Item {
        override val type = Type.PRODUCT
        override val title by product::title
    }
}

private sealed interface VariantBulkOperation {
    class Create(val variant: UnsavedShopifyProductVariant) : VariantBulkOperation
    class Update(val variant: ShopifyProductVariant) : VariantBulkOperation
    class Delete(val variant: ShopifyProductVariant) : VariantBulkOperation
}

private inline fun <reified T : VariantBulkOperation, V> List<VariantBulkOperation?>.allOf(property: KProperty1<T, V>) =
    asSequence().filterIsInstance<T>().map { property.get(it) }.toList().takeIf { it.isNotEmpty() }