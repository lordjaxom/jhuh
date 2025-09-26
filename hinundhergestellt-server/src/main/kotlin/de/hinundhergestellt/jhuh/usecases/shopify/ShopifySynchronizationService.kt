package de.hinundhergestellt.jhuh.usecases.shopify

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.mapping.ifChanged
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.backend.syncdb.update
import de.hinundhergestellt.jhuh.tools.RectPct
import de.hinundhergestellt.jhuh.tools.ShopifyImageTools
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.findById
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import kotlin.reflect.KMutableProperty0

private val logger = KotlinLogging.logger {}

@Service
@VaadinSessionScope
class ShopifySynchronizationService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val shopifyImageTools: ShopifyImageTools,
    private val shopifyMapper: ShopifyMapper,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val shopTexterService: ShopTexterService,
    private val mappingService: MappingService,
    private val transactionOperations: TransactionOperations
) {
    val items = mutableListOf<Item>()

    init {
        // TODO: Refresh when other service refreshes data stores
        syncProductRepository.findAllBySyncedIsTrue().forEach { synchronize(it) }
    }

    suspend fun refresh(report: suspend (String) -> Unit) {
        report("Aktualisiere Shopify- und ready2order-Produktkataloge...")
        coroutineScope {
            val job = launch { shopifyDataStore.refreshAndAwait() }
            artooDataStore.refreshAndAwait()
            job.join()
        }

        // TODO: Missing SyncVariants for variations new in ready2order (would report mapping error anyway, necessary?)

        synchronize(report)
    }

    suspend fun synchronize(report: suspend (String) -> Unit) {
        report("Gleiche synchronisierte Produkte mit Shopify ab...")
        items.clear()
        syncProductRepository.findAllBySyncedIsTrue().forEach { synchronize(it) }
    }

    suspend fun apply(items: Set<Item>, report: suspend (String) -> Unit) {
        report("Übernehme markierte Änderungen nach Shopify...")

        val createdProducts = mutableSetOf<ShopifyProduct>()
        val productsToChange = mutableSetOf<ShopifyProduct>()
        val variantsToDelete = mutableMapOf<ShopifyProduct, MutableSet<DeleteVariantItem>>()
        val variantsToCreate = mutableMapOf<ShopifyProduct, MutableSet<CreateVariantItem>>()
        val variantsToUpdate = mutableMapOf<ShopifyProduct, MutableSet<UpdateVariantItem>>()
        items.forEach { item ->
            when (item) {
                is DeleteProductItem -> apply(item)
                is CreateProductItem -> createdProducts += apply(item)
                is UpdateProductItem -> productsToChange.add(item.apply { block() }.product)
                is DeleteVariantItem -> variantsToDelete.getOrPut(item.product) { mutableSetOf() }.add(item)
                is CreateVariantItem -> variantsToCreate.getOrPut(item.product) { mutableSetOf() }.add(item)
                is UpdateVariantItem -> variantsToUpdate.getOrPut(item.product) { mutableSetOf() }.add(item.apply { block() })
                is VariantProductItem -> {}
            }
        }

        productsToChange.forEach { shopifyDataStore.update(it) }
        variantsToDelete.forEach { (product, items) -> apply(product, items) }
        variantsToCreate.forEach { (product, items) -> apply(product, items) }
        variantsToUpdate.forEach { (product, items) -> shopifyDataStore.update(product, items.map { it.variant }) }

        val newOrChangedProducts =
            createdProducts + productsToChange + variantsToDelete.keys + variantsToCreate.keys + variantsToUpdate.keys
        shopTexterService.updateProducts(newOrChangedProducts)
    }

    private fun synchronize(syncProduct: SyncProduct) {
        val artooProduct = syncProduct.artooId?.let { artooDataStore.findProductById(it) }
        val shopifyProduct = syncProduct.shopifyId?.let { shopifyDataStore.findProductById(it) }

        if (artooProduct != null && mappingService.checkForProblems(artooProduct, syncProduct).isNotEmpty()) {
            logger.warn { "Product ${artooProduct.name} has problems, skip synchronization" }
            return
        }

        if (artooProduct == null) {
            require(shopifyProduct != null) { "SyncProduct vanished from both ready2order and Shopify" }
            items += DeleteProductItem(syncProduct, shopifyProduct)
            return
        }

        if (shopifyProduct == null) {
            items += CreateProductItem(syncProduct, artooProduct)
            return
        }

        require(shopifyProduct.hasOnlyDefaultVariant == artooProduct.hasOnlyDefaultVariant) { "Switching variants and standalone not supported yet" }

        items += listOfNotNull(
            synchronize(shopifyProduct, shopifyProduct::title, artooProduct.description),
            synchronize(shopifyProduct, shopifyProduct::vendor, syncProduct.vendor!!.name),
            synchronize(shopifyProduct, shopifyProduct::productType, syncProduct.type!!),
            synchronize(shopifyProduct, shopifyProduct::descriptionHtml, syncProduct.descriptionHtml ?: ""),
            synchronize(shopifyProduct, shopifyProduct::tags, mappingService.allTags(syncProduct, artooProduct))
        )
        items += mappingService.customMetafields(syncProduct).mapNotNull { synchronize(shopifyProduct, it) }

        syncProduct.variants
            .flatMap { synchronize(it, artooProduct, shopifyProduct) }
            .takeIf { it.isNotEmpty() }
            ?.also { items += VariantProductItem(shopifyProduct, it) }
    }

    private fun synchronize(
        syncVariant: SyncVariant,
        artooProduct: ArtooMappedProduct,
        shopifyProduct: ShopifyProduct
    ): List<VariantItem> {
        val artooVariation = artooProduct.findVariationByBarcode(syncVariant.barcode)
        val shopifyVariant = shopifyProduct.findVariantByBarcode(syncVariant.barcode)

        if (artooVariation != null &&
            !artooProduct.hasOnlyDefaultVariant &&
            mappingService.checkForProblems(artooVariation, syncVariant).any { it.error }
        ) {
            logger.warn { "Variant ${artooProduct.name} (${artooVariation.name}) has problems, skip synchronization" }
            return listOf()
        }

//        if (artooVariation == null && shopifyVariant == null) {
//            logger.info { "Variant of ${artooProduct.name} with barcode ${syncVariant.barcode} vanished, forget" }
//            syncVariant.product.variants.remove(syncVariant)
//            return null
//        }

        if (artooVariation == null) {
            require(shopifyVariant != null) { "SyncVariant vanished from both ready2order and Shopify" }
            return listOf(DeleteVariantItem(shopifyProduct, syncVariant, shopifyVariant))
        }

        if (shopifyVariant == null) {
            return listOf(CreateVariantItem(shopifyProduct, syncVariant, artooVariation))
        }

        return listOfNotNull(
            synchronize(shopifyProduct, shopifyVariant, shopifyVariant::barcode, artooVariation.barcode!!),
            synchronize(shopifyProduct, shopifyVariant, shopifyVariant::sku, artooVariation.itemNumber ?: ""),
            synchronize(shopifyProduct, shopifyVariant, shopifyVariant::price, artooVariation.price),
            synchronize(shopifyProduct, shopifyVariant, shopifyVariant::weight, syncVariant.weight!!),
//            prepareUpdateVariantOptionValue(shopifyProduct, shopifyVariant, artooVariation)
        )
    }

    private fun <T> synchronize(product: ShopifyProduct, property: KMutableProperty0<T>, newValue: T) =
        ifChanged(property.get(), newValue, property.name) { UpdateProductItem(product, it) { property.set(newValue) } }

    private fun synchronize(product: ShopifyProduct, newField: ShopifyMetafield) =
        product.metafields.findById(newField).let { oldField ->
            ifChanged(oldField?.value, newField.value, newField.key) {
                UpdateProductItem(product, it) {
                    if (oldField != null) oldField.value = newField.value
                    else product.metafields.add(newField)
                }
            }
        }

    private fun <T> synchronize(product: ShopifyProduct, variant: ShopifyProductVariant, property: KMutableProperty0<T>, newValue: T) =
        ifChanged(property.get(), newValue, property.name) { UpdateVariantItem(product, variant, it) { property.set(newValue) } }

//    private fun prepareUpdateVariantOptionValue(
//        product: ShopifyProduct,
//        variant: ShopifyProductVariant,
//        artooVariation: ArtooMappedVariation
//    ) = when {
//        artooVariation.isDefaultVariant -> null
//        else -> change(variant.options[0]::value, artooVariation.name, ChangeField.OPTION_VALUE, variant.options[0].name)
//            ?.let { UpdateVariantItem(product, variant, it.message, it.action) }
//    }

    private suspend fun apply(item: DeleteProductItem) {
        shopifyDataStore.delete(item.shopify)
        shopTexterService.removeProduct(item.shopify.id)
        transactionOperations.execute { syncProductRepository.delete(item.sync) }
    }

    private suspend fun apply(item: CreateProductItem): ShopifyProduct {
        val product = shopifyDataStore.create(shopifyMapper.map(item.sync, item.artoo))
        shopifyImageTools.uploadProductImages(product)

        transactionOperations.execute {
            syncProductRepository.update(item.sync.id) { shopifyId = product.id; descriptionHtml = product.descriptionHtml }
        }

        apply(product, item.sync.variants.mapNotNull { variant ->
            variant.artooId
                ?.let { item.artoo.findVariationById(it) }
                ?.let { CreateVariantItem(product, variant, it) }
        })

        return product
    }

    @JvmName("applyDelete")
    private suspend fun apply(product: ShopifyProduct, items: Collection<DeleteVariantItem>) {
        shopifyDataStore.delete(product, items.map { it.shopify })
        transactionOperations.execute { syncVariantRepository.deleteAll(items.map { it.sync }) }
    }

    @JvmName("applyCreate")
    private suspend fun apply(product: ShopifyProduct, items: Collection<CreateVariantItem>) {
        val variants = items.map { shopifyMapper.map(it.sync, it.artoo) }
        shopifyImageTools.uploadVariantImages(product, variants)
        shopifyImageTools.generateColorSwatches(product, variants, RectPct.CENTER_20)

        shopifyDataStore.create(product, variants)
        transactionOperations.execute {
            variants.asSequence()
                .zip(items.asSequence())
                .forEach { (variant, item) -> syncVariantRepository.update(item.sync.id) { shopifyId = variant.id } }
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

    private inner class DeleteProductItem(
        val sync: SyncProduct,
        val shopify: ShopifyProduct
    ) : ProductItem() {
        override val title by shopify::title
        override val message = "Produkt entfernt"
    }

    private inner class CreateProductItem(
        val sync: SyncProduct,
        val artoo: ArtooMappedProduct
    ) : ProductItem() {
        override val title = artoo.description
        override val message =
            "Produkt${if (!artoo.hasOnlyDefaultVariant) " mit bis zu ${artoo.variations.size} Varianten" else ""} hinzugefügt"
    }

    private inner class UpdateProductItem(
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
        override val message = "Insg. ${children.size} Änderungen in Varianten"
    }

    sealed class VariantItem : Item {
        override val children = listOf<Item>()
    }

    private inner class DeleteVariantItem(
        val product: ShopifyProduct,
        val sync: SyncVariant,
        val shopify: ShopifyProductVariant
    ) : VariantItem() {
        override val title = "${shopify.options[0].name} ${shopify.options[0].value}"
        override val message = "$title entfernt"
    }

    private inner class CreateVariantItem(
        val product: ShopifyProduct,
        val sync: SyncVariant,
        val artoo: ArtooMappedVariation
    ) : VariantItem() {
        override val title = "${sync.product.optionName} ${artoo.name}"
        override val message = "$title hinzugefügt"
    }

    private inner class UpdateVariantItem(
        val product: ShopifyProduct,
        val variant: ShopifyProductVariant,
        override val message: String,
        val block: () -> Unit
    ) : VariantItem() {
        override val title = "${variant.options[0].name} ${variant.options[0].value}"
    }
}