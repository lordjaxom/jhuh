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
import de.hinundhergestellt.jhuh.tools.SyncImage
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
        items += syncProductRepository.findAllBySyncedIsTrue().mapNotNull { synchronize(it) }
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
        items += syncProductRepository.findAllBySyncedIsTrue().mapNotNull { synchronize(it) }
    }

    suspend fun apply(items: Set<Item>, report: suspend (String) -> Unit) {
        report("Übernehme markierte Änderungen nach Shopify...")

        val createdProducts = mutableSetOf<ShopifyProduct>()
        val productsToUpdate = mutableSetOf<UpdateProductItem>()
        val variantsToDelete = mutableMapOf<ShopifyProduct, MutableSet<DeleteVariantItem>>()
        val variantsToCreate = mutableMapOf<ShopifyProduct, MutableSet<CreateVariantItem>>()
        val variantsToUpdate = mutableMapOf<ShopifyProduct, MutableSet<UpdateVariantItem>>()
        items.forEach { item ->
            when (item) {
                is ProductHeaderItem -> {}
                is VariantHeaderItem -> {}
                is DeleteProductItem -> apply(item)
                is CreateProductItem -> createdProducts += apply(item)
                is UpdateProductItem -> productsToUpdate.add(item.apply { block() })
                is UpdateImagesItem -> apply(item)
                is DeleteVariantItem -> variantsToDelete.getOrPut(item.product) { mutableSetOf() }.add(item)
                is CreateVariantItem -> variantsToCreate.getOrPut(item.product) { mutableSetOf() }.add(item)
                is UpdateVariantItem -> variantsToUpdate.getOrPut(item.product) { mutableSetOf() }.add(item.apply { block() })
            }
        }

        productsToUpdate.forEach { apply(it) }
        variantsToDelete.forEach { (product, items) -> apply(product, items) }
        variantsToCreate.forEach { (product, items) -> apply(product, items) }
        variantsToUpdate.forEach { (product, items) -> shopifyDataStore.update(product, items.map { it.variant }) }

        val newOrChangedProducts =
            createdProducts + productsToUpdate.map { it.shopify } + variantsToDelete.keys + variantsToCreate.keys + variantsToUpdate.keys
        shopTexterService.updateProducts(newOrChangedProducts)
    }

    private fun synchronize(syncProduct: SyncProduct): ProductHeaderItem? {
        val artooProduct = syncProduct.artooId?.let { artooDataStore.findProductById(it) }
        val shopifyProduct = syncProduct.shopifyId?.let { shopifyDataStore.findProductById(it) }

        if (artooProduct != null && mappingService.checkForProblems(artooProduct, syncProduct).isNotEmpty()) {
            logger.warn { "Product ${artooProduct.name} has problems, skip synchronization" }
            return null
        }

        if (artooProduct == null) {
            require(shopifyProduct != null) { "SyncProduct vanished from both ready2order and Shopify" }
            return ProductHeaderItem(shopifyProduct.title, listOf(DeleteProductItem(syncProduct, shopifyProduct)))
        }

        if (shopifyProduct == null) {
            return ProductHeaderItem(artooProduct.description, listOf(CreateProductItem(syncProduct, artooProduct)))
        }

        require(shopifyProduct.hasOnlyDefaultVariant == artooProduct.hasOnlyDefaultVariant) { "Switching variants and standalone not supported yet" }

        val items = mutableListOf<Item>()
        items += listOfNotNull(
            synchronize(syncProduct, shopifyProduct, shopifyProduct::title, artooProduct.description),
            synchronize(syncProduct, shopifyProduct, shopifyProduct::vendor, syncProduct.vendor!!.name),
            synchronize(syncProduct, shopifyProduct, shopifyProduct::productType, syncProduct.type!!),
            synchronize(syncProduct, shopifyProduct, shopifyProduct::descriptionHtml, syncProduct.descriptionHtml ?: ""),
            synchronize(syncProduct, shopifyProduct, shopifyProduct::seoTitle, syncProduct.seoTitle ?: ""),
            synchronize(syncProduct, shopifyProduct, shopifyProduct::seoDescription, syncProduct.metaDescription ?: ""),
            synchronize(syncProduct, shopifyProduct, shopifyProduct::tags, mappingService.allTags(syncProduct, artooProduct))
        )
        items += mappingService.productMetafields(syncProduct).mapNotNull { synchronize(syncProduct, shopifyProduct, it) }

        shopifyImageTools.remotelyMissingProductImages(shopifyProduct)
            .takeIf { it.isNotEmpty() }
            ?.let { items += UpdateImagesItem(shopifyProduct, it) }

        items += syncProduct.variants
            .mapNotNull { synchronize(it, artooProduct, shopifyProduct) }
            .run { if (artooProduct.hasOnlyDefaultVariant) flatMap { it.children } else this } // collapse default variant into product

        return if (items.isNotEmpty()) ProductHeaderItem(artooProduct.description, items) else null
    }

    private fun synchronize(
        syncVariant: SyncVariant,
        artooProduct: ArtooMappedProduct,
        shopifyProduct: ShopifyProduct
    ): VariantHeaderItem? {
        val artooVariation = artooProduct.findVariationByBarcode(syncVariant.barcode)
        val shopifyVariant = shopifyProduct.findVariantByBarcode(syncVariant.barcode)

        if (artooVariation != null &&
            !artooProduct.hasOnlyDefaultVariant &&
            mappingService.checkForProblems(artooVariation, syncVariant).any { it.error }
        ) {
            logger.warn { "Variant ${artooProduct.name} (${artooVariation.name}) has problems, skip synchronization" }
            return null
        }

//        if (artooVariation == null && shopifyVariant == null) {
//            logger.info { "Variant of ${artooProduct.name} with barcode ${syncVariant.barcode} vanished, forget" }
//            syncVariant.product.variants.remove(syncVariant)
//            return null
//        }

        if (artooVariation == null) {
            require(shopifyVariant != null) { "SyncVariant vanished from both ready2order and Shopify" }
            val title = "${syncVariant.product.optionName} ${shopifyVariant.options[0].value}"
            return VariantHeaderItem(title, listOf(DeleteVariantItem(shopifyProduct, syncVariant, shopifyVariant)))
        }

        val title = "${syncVariant.product.optionName} ${artooVariation.name}"
        if (shopifyVariant == null) {
            return VariantHeaderItem(title, listOf(CreateVariantItem(shopifyProduct, syncVariant, artooVariation)))
        }

        val items = listOfNotNull(
            synchronize(shopifyProduct, shopifyVariant, shopifyVariant::barcode, artooVariation.barcode!!),
            synchronize(shopifyProduct, shopifyVariant, shopifyVariant::sku, artooVariation.itemNumber ?: ""),
            synchronize(shopifyProduct, shopifyVariant, shopifyVariant::price, artooVariation.price),
            synchronize(shopifyProduct, shopifyVariant, shopifyVariant::weight, syncVariant.weight!!),
//            prepareUpdateVariantOptionValue(shopifyProduct, shopifyVariant, artooVariation)
        )

        return if (items.isNotEmpty()) VariantHeaderItem(title, items) else null
    }

    private fun <T> synchronize(sync: SyncProduct, shopify: ShopifyProduct, property: KMutableProperty0<T>, newValue: T) =
        ifChanged(property.get(), newValue, property.name) { UpdateProductItem(sync, shopify, it) { property.set(newValue) } }

    private fun synchronize(sync: SyncProduct, shopify: ShopifyProduct, newField: ShopifyMetafield) =
        shopify.metafields.findById(newField).let { oldField ->
            ifChanged(oldField?.value, newField.value, "${newField.namespace}:${newField.key}") {
                UpdateProductItem(sync, shopify, it) {
                    if (oldField != null) oldField.value = newField.value
                    else shopify.metafields.add(newField)
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
        require(item.artoo.hasOnlyDefaultVariant) { "TODO: Create appropriate product options when creating a new product with variants" }

        val product = shopifyMapper.map(item.sync, item.artoo)
        shopifyDataStore.create(product)
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

    private suspend fun apply(item: UpdateProductItem) {
        shopifyDataStore.update(item.shopify)
        transactionOperations.execute { syncProductRepository.update(item.sync.id) { descriptionHtml = item.shopify.descriptionHtml } }
    }

    private suspend fun apply(item: UpdateImagesItem) {
        shopifyImageTools.uploadProductImages(item.product, item.images)
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
        val message: String
        val children: List<Item>
    }

    sealed class HeaderItem(
        override val message: String,
        override val children: List<Item>
    ) : Item

    class ProductHeaderItem(title: String, children: List<Item>) : HeaderItem(title, children)
    class VariantHeaderItem(title: String, children: List<Item>) : HeaderItem(title, children)

    sealed class ChangeItem(
        override val message: String
    ) : Item {
        override val children = listOf<Item>()
    }

    private class DeleteProductItem(
        val sync: SyncProduct,
        val shopify: ShopifyProduct
    ) : ChangeItem("Produkt entfernt")

    private class CreateProductItem(
        val sync: SyncProduct,
        val artoo: ArtooMappedProduct
    ) : ChangeItem("Produkt${if (!artoo.hasOnlyDefaultVariant) " mit bis zu ${artoo.variations.size} Varianten" else ""} hinzugefügt")

    private class UpdateProductItem(
        val sync: SyncProduct,
        val shopify: ShopifyProduct,
        message: String,
        val block: () -> Unit,
    ) : ChangeItem(message)

    private class UpdateImagesItem(
        val product: ShopifyProduct,
        val images: List<SyncImage>
    ) : ChangeItem("${images.size} Produktbilder hinzugefügt")

    private class DeleteVariantItem(
        val product: ShopifyProduct,
        val sync: SyncVariant,
        val shopify: ShopifyProductVariant
    ) : ChangeItem("Variante entfernt")

    private class CreateVariantItem(
        val product: ShopifyProduct,
        val sync: SyncVariant,
        val artoo: ArtooMappedVariation
    ) : ChangeItem("Variante hinzugefügt")

    private class UpdateVariantItem(
        val product: ShopifyProduct,
        val variant: ShopifyProductVariant,
        message: String,
        val block: () -> Unit
    ) : ChangeItem(message)
}