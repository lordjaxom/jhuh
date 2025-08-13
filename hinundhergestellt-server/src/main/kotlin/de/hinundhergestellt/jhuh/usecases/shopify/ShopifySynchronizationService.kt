package de.hinundhergestellt.jhuh.usecases.shopify

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.barcodes.BarcodeGenerator
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
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
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.isDryRun
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import kotlin.reflect.KProperty1

private val logger = KotlinLogging.logger {}

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
    val items = mutableListOf<ProductItem>()

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
        items.forEach { it.action() }
    }

    private suspend fun synchronize(syncProduct: SyncProduct) {
        val artooProduct = syncProduct.artooId?.let { artooDataStore.findProductById(it) }
        val shopifyProduct = syncProduct.shopifyId?.let { shopifyDataStore.findProductById(it) }

        logger.info { "Sync ${syncProduct.id} ${artooProduct?.name} ${shopifyProduct?.title}"}

        if (artooProduct != null && mappingService.checkForProblems(artooProduct, syncProduct).isNotEmpty()) {
            logger.warn { "Product ${artooProduct.name} has problems, skip synchronization" }
            return
        }

        if (artooProduct == null) {
            require(shopifyProduct != null) { "SyncProduct vanished from both ready2order and Shopify" }
            items += ProductItem(shopifyProduct.title, "Produkt wird in Shopify gelöscht") {
                shopifyDataStore.delete(shopifyProduct)
                shopTexterService.removeProduct(syncProduct.id)
                transactionOperations.execute { syncProductRepository.delete(syncProduct) }
            }
            return
        }

        if (shopifyProduct == null) {
            val unsavedShopifyProduct = shopifyProductMapper.mapToProduct(syncProduct, artooProduct)
            val unsavedVariants = syncProduct.variants
                .mapNotNull { variant -> variant.artooId?.let { artooProduct.findVariationById(it) }?.let { variant to it } }
                .map { (sync, artoo) -> sync to shopifyVariantMapper.mapToVariant(artooProduct, sync,artoo) }
            val unsavedVariantsText = if (!artooProduct.hasOnlyDefaultVariant) "mit ${unsavedVariants.size} Varianten " else ""

            items += ProductItem(unsavedShopifyProduct.title, "Produkt wird ${unsavedVariantsText}in Shopify neu erstellt") {
                val savedShopifyProduct = shopifyDataStore.create(unsavedShopifyProduct)
                if (!savedShopifyProduct.isDryRun) syncProduct.shopifyId = savedShopifyProduct.id
                shopifyDataStore.create(savedShopifyProduct, unsavedVariants.map { it.second })
                if (!savedShopifyProduct.isDryRun)
                    unsavedVariants.forEachIndexed { index, (sync, _) -> sync.shopifyId = savedShopifyProduct.variants[index].id }
                shopTexterService.updateProduct(syncProduct.id, savedShopifyProduct)
                transactionOperations.execute { syncProductRepository.save(syncProduct) }
            }
            return
        }



        else if (shopifyProductMapper.updateProduct(syncProduct, artooProduct, shopifyProduct)) {
            logger.info { "Product ${artooProduct.name} has changed, update in Shopify" }
//            runBlocking { shopifyDataStore.update(shopifyProduct) }
//            shopTexterService.updateProduct(syncProduct.id, shopifyProduct)
        }
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

    sealed interface Item {
        val title: String
        val message: String
        val action: suspend () -> Unit
    }

    inner class ProductItem(
        override val title: String,
        override val message: String,
        override val action: suspend () -> Unit
    ) : Item {

    }
}

private sealed interface VariantBulkOperation {
    class Create(val variant: UnsavedShopifyProductVariant) : VariantBulkOperation
    class Update(val variant: ShopifyProductVariant) : VariantBulkOperation
    class Delete(val variant: ShopifyProductVariant) : VariantBulkOperation
}

private inline fun <reified T : VariantBulkOperation, V> List<VariantBulkOperation?>.allOf(property: KProperty1<T, V>) =
    asSequence().filterIsInstance<T>().map { property.get(it) }.toList().takeIf { it.isNotEmpty() }