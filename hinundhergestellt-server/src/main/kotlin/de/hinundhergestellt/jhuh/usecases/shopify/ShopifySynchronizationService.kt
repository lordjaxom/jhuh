package de.hinundhergestellt.jhuh.usecases.shopify

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.mapping.additionMessage
import de.hinundhergestellt.jhuh.backend.mapping.changeMessage
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
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
import java.util.UUID
import kotlin.reflect.KMutableProperty0

private val logger = KotlinLogging.logger {}

private val PROPERTY_TO_FIELD = mapOf(
    "title" to Pair("Titel", true),
    "vendor" to Pair("Hersteller", true),
    "productType" to Pair("Produktart", true),
    "descriptionHtml" to Pair("Beschreibung", false),
    "tags" to Pair("Tags", true),
    "vendor_email" to Pair("Hersteller-Email", true),
    "vendor_address" to Pair("Herstelleradresse", false),
    "product_specs" to Pair("Produktspezifikationen", false),
    "barcode" to Pair("Barcode", true),
    "sku" to Pair("Artikelnummer", true),
    "price" to Pair("Preis", true)
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

        val productsToChange = mutableSetOf<ShopifyProduct>()
        val variantsToDelete = mutableMapOf<ShopifyProduct, MutableMap<UUID, ShopifyProductVariant>>()
        val variantsToCreate = mutableMapOf<ShopifyProduct, MutableMap<UUID, UnsavedShopifyProductVariant>>()
        val variantsToUpdate = mutableMapOf<ShopifyProduct, MutableSet<ShopifyProductVariant>>()
        items.forEach { item ->
            when (item) {
                is ImmediateItem -> item.block()
                is DeferredProductItem -> productsToChange.add(item.apply { block() }.product)
                is DeleteVariantItem -> variantsToDelete.getOrPut(item.product) { mutableMapOf() }.put(item.id, item.variant)
                is CreateVariantItem -> variantsToCreate.getOrPut(item.product) { mutableMapOf() }.put(item.id, item.variant)
                is UpdateVariantItem -> variantsToUpdate.getOrPut(item.product) { mutableSetOf() }.add(item.apply { block() }.variant)
                is VariantProductItem -> {}
            }
        }

        productsToChange.forEach { shopifyDataStore.update(it) }
        variantsToDelete.forEach { (product, variants) -> applyDeleteVariants(product, variants) }
        variantsToCreate.forEach { (product, variants) -> applyCreateVariants(product, variants) }
        variantsToUpdate.forEach { (product, variants) -> shopifyDataStore.update(product, variants) }
    }

    private suspend fun synchronize(syncProduct: SyncProduct) {
        val artooProduct = syncProduct.artooId?.let { artooDataStore.findProductById(it) }
        val shopifyProduct = syncProduct.shopifyId?.let { shopifyDataStore.findProductById(it) }

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

        syncProduct.variants
            .flatMap { synchronize(it, artooProduct, shopifyProduct) }
            .takeIf { it.isNotEmpty() }
            ?.also { items += VariantProductItem(shopifyProduct, it) }
    }

    private fun synchronize(syncVariant: SyncVariant, artooProduct: ArtooMappedProduct, shopifyProduct: ShopifyProduct)
            : List<VariantItem> {
        val artooVariation = artooProduct.findVariationByBarcode(syncVariant.barcode)
        val shopifyVariant = shopifyProduct.findVariantByBarcode(syncVariant.barcode)

//        if (artooVariation == null && shopifyVariant == null) {
//            logger.info { "Variant of ${artooProduct.name} with barcode ${syncVariant.barcode} vanished, forget" }
//            syncVariant.product.variants.remove(syncVariant)
//            return null
//        }

        if (artooVariation == null) {
            require(shopifyVariant != null) { "SyncVariant vanished from both ready2order and Shopify" }
            return listOf(DeleteVariantItem(shopifyProduct, shopifyVariant, syncVariant.id))
        }

        if (shopifyVariant == null) {
            val unsavedShopifyVariant = shopifyVariantMapper.mapToVariant(artooProduct, syncVariant, artooVariation)
            return listOf(CreateVariantItem(shopifyProduct, unsavedShopifyVariant, syncVariant.id))
        }

        return listOfNotNull(
            prepareUpdateVariantProperty(shopifyProduct, shopifyVariant, shopifyVariant::barcode, artooVariation.barcode!!),
            prepareUpdateVariantProperty(shopifyProduct, shopifyVariant, shopifyVariant::sku, artooVariation.itemNumber ?: ""),
            prepareUpdateVariantProperty(shopifyProduct, shopifyVariant, shopifyVariant::price, artooVariation.price),
            prepareUpdateVariantOptionValue(shopifyProduct, shopifyVariant, artooVariation)
        )
    }

    private fun prepareDeleteProduct(syncProduct: SyncProduct, shopifyProduct: ShopifyProduct) {
        items += ImmediateItem(shopifyProduct.title, "Produkt entfernt") {
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

        items += ImmediateItem(unsavedShopifyProduct.title, "Produkt${variantsText} hinzugefügt") {
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
        items += DeferredProductItem(shopifyProduct, property.changeMessage(newValue)) { property.set(newValue) }
    }

    private fun prepareUpdateProductMetafield(
        shopifyProduct: ShopifyProduct,
        newMetafield: ShopifyMetafield
    ) {
        val oldMetafield = shopifyProduct.metafields.findById(newMetafield)
        if (oldMetafield == null) {
            val message = additionMessage(newMetafield.key, newMetafield.value)
            items += DeferredProductItem(shopifyProduct, message) { shopifyProduct.metafields.add(newMetafield) }
        } else if (oldMetafield.value != newMetafield.value) {
            val message = changeMessage(newMetafield.key, oldMetafield.value, newMetafield.value)
            items += DeferredProductItem(shopifyProduct, message) { oldMetafield.value = newMetafield.value }
        }
    }

    private fun <T> prepareUpdateVariantProperty(
        product: ShopifyProduct,
        variant: ShopifyProductVariant,
        property: KMutableProperty0<T>,
        newValue: T
    ): VariantItem? {
        if (property.get() == newValue) return null
        return UpdateVariantItem(product, variant, property.changeMessage(newValue)) { property.set(newValue) }
    }

    private fun prepareUpdateVariantOptionValue(
        shopifyProduct: ShopifyProduct,
        shopifyVariant: ShopifyProductVariant,
        artooVariation: ArtooMappedVariation
    ) = when {
        artooVariation.isDefaultVariant -> null
        else -> prepareUpdateVariantProperty(shopifyProduct, shopifyVariant, shopifyVariant.options[0]::value, artooVariation.name)
    }

    private suspend fun applyDeleteVariants(
        product: ShopifyProduct,
        variants: MutableMap<UUID, ShopifyProductVariant>
    ) {
        shopifyDataStore.delete(product, variants.values)
        transactionOperations.execute { syncVariantRepository.deleteAllById(variants.keys) }
    }

    private suspend fun applyCreateVariants(
        product: ShopifyProduct,
        variants: MutableMap<UUID, UnsavedShopifyProductVariant>
    ) {
        val created = shopifyDataStore.create(product, variants.values)
        transactionOperations.execute {
            created.map { it.id }.zip(variants.keys).forEach { (shopifyId, syncId) ->
                val syncVariant = syncVariantRepository.findById(syncId).orElseThrow()
                syncVariant.shopifyId = shopifyId
                syncVariantRepository.save(syncVariant)
            }
        }
    }

    sealed interface Item {
        val title: String
        val message: String
        val children: List<Item>
    }

    sealed class ProductItem : Item {
        override val children = listOf<Item>()
    }

    private inner class ImmediateItem(
        override val title: String,
        override val message: String,
        val block: suspend () -> Unit
    ) : ProductItem()

    private inner class DeferredProductItem(
        val product: ShopifyProduct,
        override val message: String,
        val block: () -> Unit,
    ) : ProductItem() {
        override val title by product::title
    }

    private inner class VariantProductItem(
        product: ShopifyProduct,
        override val children: List<VariantItem>
    ) : ProductItem() {
        override val title by product::title
        override val message = "Varianten geändert"
    }

    sealed class VariantItem : Item {
        override val children = listOf<Item>()
    }

    private inner class DeleteVariantItem(
        val product: ShopifyProduct,
        val variant: ShopifyProductVariant,
        val id: UUID
    ) : VariantItem() {
        override val title by variant::title
        override val message = "Variante $title entfernt"
    }

    private inner class CreateVariantItem(
        val product: ShopifyProduct,
        val variant: UnsavedShopifyProductVariant,
        val id: UUID
    ) : VariantItem() {
        override val title = variant.options[0].value
        override val message = "Variante $title hinzugefügt"
    }

    private inner class UpdateVariantItem(
        val product: ShopifyProduct,
        val variant: ShopifyProductVariant,
        override val message: String,
        val block: () -> Unit
    ) : VariantItem() {
        override val title by variant::title
    }
}