package de.hinundhergestellt.jhuh.usecases.shopify

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.mapping.ChangeField
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.mapping.addition
import de.hinundhergestellt.jhuh.backend.mapping.change
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.tools.RectPct
import de.hinundhergestellt.jhuh.tools.ShopifyImageTools
import de.hinundhergestellt.jhuh.tools.SyncImageTools
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyWeight
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.findById
import de.hinundhergestellt.jhuh.vendors.shopify.client.isDryRun
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.util.UUID
import kotlin.reflect.KMutableProperty0

private val logger = KotlinLogging.logger {}

@Service
@VaadinSessionScope
class ShopifySynchronizationService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val shopifyImageTools: ShopifyImageTools,
    private val shopifyProductMapper: ShopifyProductMapper,
    private val shopifyVariantMapper: ShopifyVariantMapper,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val shopTexterService: ShopTexterService,
    private val mappingService: MappingService,
    private val transactionOperations: TransactionOperations
) {
    val items = mutableListOf<Item>()

    suspend fun refresh(report: suspend (String) -> Unit) {
        report("Aktualisiere Shopify- und ready2order-Produktkataloge...")
        coroutineScope {
            val job = launch { shopifyDataStore.refreshAndAwait() }
            artooDataStore.refreshAndAwait()
            job.join()
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
                is UpdateProductItem -> productsToChange.add(item.apply { block() }.product)
                is DeleteVariantItem -> variantsToDelete.getOrPut(item.product) { mutableMapOf() }.put(item.id, item.variant)
                is CreateVariantItem -> variantsToCreate.getOrPut(item.product) { mutableMapOf() }.put(item.id, item.variant)
                is UpdateVariantItem -> variantsToUpdate.getOrPut(item.product) { mutableSetOf() }.add(item.apply { block() }.variant)
                is VariantProductItem -> {}
            }
        }

        // TODO: Update all changed products together in shopTexter, and remove deleted products
        productsToChange.forEach { shopifyDataStore.update(it); shopTexterService.updateProduct(it) }
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

        require(shopifyProduct.hasOnlyDefaultVariant == artooProduct.hasOnlyDefaultVariant) { "Switching variants and standalone not supported yet" }

        items += listOfNotNull(
            prepareUpdateProductProperty(shopifyProduct, shopifyProduct::title, artooProduct.description),
            prepareUpdateProductProperty(shopifyProduct, shopifyProduct::vendor, syncProduct.vendor!!.name),
            prepareUpdateProductProperty(shopifyProduct, shopifyProduct::productType, syncProduct.type!!),
            prepareUpdateProductProperty(shopifyProduct, shopifyProduct::descriptionHtml, syncProduct.descriptionHtml ?: ""),
            prepareUpdateProductProperty(shopifyProduct, shopifyProduct::tags, mappingService.allTags(syncProduct, artooProduct))
        )
        items += mappingService.customMetafields(syncProduct).mapNotNull { prepareUpdateProductMetafield(shopifyProduct, it) }

        syncProduct.variants
            .flatMap { synchronize(syncProduct, it, artooProduct, shopifyProduct) }
            .takeIf { it.isNotEmpty() }
            ?.also { items += VariantProductItem(shopifyProduct, it) }
    }

    private fun synchronize(
        syncProduct: SyncProduct,
        syncVariant: SyncVariant,
        artooProduct: ArtooMappedProduct,
        shopifyProduct: ShopifyProduct
    ): List<VariantItem> {
        val artooVariation = artooProduct.findVariationByBarcode(syncVariant.barcode)
        val shopifyVariant = shopifyProduct.findVariantByBarcode(syncVariant.barcode)

        if (artooVariation != null && mappingService.checkForProblems(artooVariation, syncVariant, artooProduct).any { it.error }) {
            logger.warn { "Variant ${artooProduct.name} (${artooVariation.name}) has problems, skip synchronization"}
            return listOf()
        }

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
            val unsavedShopifyVariant = shopifyVariantMapper.mapToVariant(syncProduct, artooProduct, syncVariant, artooVariation)
            return listOf(CreateVariantItem(shopifyProduct, unsavedShopifyVariant, syncVariant.id))
        }

        return listOfNotNull(
            prepareUpdateVariantProperty(shopifyProduct, shopifyVariant, shopifyVariant::barcode, artooVariation.barcode!!),
            prepareUpdateVariantProperty(shopifyProduct, shopifyVariant, shopifyVariant::sku, artooVariation.itemNumber ?: ""),
            prepareUpdateVariantProperty(shopifyProduct, shopifyVariant, shopifyVariant::price, artooVariation.price),
            prepareUpdateVariantWeight(shopifyProduct, shopifyVariant, syncVariant),
            prepareUpdateVariantOptionValue(shopifyProduct, shopifyVariant, artooVariation)
        )
    }

    private fun prepareDeleteProduct(syncProduct: SyncProduct, shopifyProduct: ShopifyProduct) {
        items += ImmediateItem(shopifyProduct.title, "Produkt entfernt") {
            shopifyDataStore.delete(shopifyProduct)
            shopTexterService.removeProduct(shopifyProduct.id)
            transactionOperations.execute { syncProductRepository.delete(syncProduct) }
        }
    }

    private fun prepareCreateProduct(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct) {
        val unsavedShopifyProduct = shopifyProductMapper.mapToProduct(syncProduct, artooProduct)
        val unsavedVariants = syncProduct.variants
            .mapNotNull { variant -> variant.artooId?.let { artooProduct.findVariationById(it) }?.let { variant to it } }
            .map { (sync, artoo) -> sync to shopifyVariantMapper.mapToVariant(syncProduct, artooProduct, sync, artoo) }
        val variantsText = if (!artooProduct.hasOnlyDefaultVariant) " mit ${unsavedVariants.size} Varianten" else ""

        items += ImmediateItem(unsavedShopifyProduct.title, "Produkt$variantsText hinzugefügt") {
            val savedShopifyProduct = shopifyDataStore.create(unsavedShopifyProduct)
            if (!savedShopifyProduct.isDryRun) syncProduct.shopifyId = savedShopifyProduct.id
            shopifyDataStore.create(savedShopifyProduct, unsavedVariants.map { it.second })
            if (!savedShopifyProduct.isDryRun)
                unsavedVariants.forEachIndexed { index, (sync, _) -> sync.shopifyId = savedShopifyProduct.variants[index].id }
            shopTexterService.updateProduct(savedShopifyProduct)
            transactionOperations.execute {
                syncProduct.descriptionHtml = savedShopifyProduct.descriptionHtml
                syncProductRepository.save(syncProduct)
            }
        }
    }

    private fun <T> prepareUpdateProductProperty(
        shopifyProduct: ShopifyProduct,
        property: KMutableProperty0<T>,
        newValue: T
    ) = change(property, newValue)?.let { UpdateProductItem(shopifyProduct, it.message, it.action) }

    private fun prepareUpdateProductMetafield(
        shopifyProduct: ShopifyProduct,
        newMetafield: ShopifyMetafield
    ): UpdateProductItem? {
        val oldMetafield = shopifyProduct.metafields.findById(newMetafield)
        return if (oldMetafield == null) {
            UpdateProductItem(shopifyProduct, addition(newMetafield.value, ChangeField.PRODUCT_TECHNICAL_DETAILS)) {
                shopifyProduct.metafields.add(newMetafield)
            }
        } else {
            change(oldMetafield::value, newMetafield.value, ChangeField.PRODUCT_TECHNICAL_DETAILS)
                ?.let { UpdateProductItem(shopifyProduct, it.message, it.action) }
        }
    }

    private fun <T> prepareUpdateVariantProperty(
        product: ShopifyProduct,
        variant: ShopifyProductVariant,
        property: KMutableProperty0<T>,
        newValue: T
    ) = change(property, newValue)?.let { UpdateVariantItem(product, variant, it.message, it.action) }

    private fun prepareUpdateVariantWeight(
        product: ShopifyProduct,
        variant: ShopifyProductVariant,
        syncVariant: SyncVariant
    ) = change(
        { variant.weight.value },
        { variant.weight = ShopifyWeight(variant.weight.unit, it) },
        syncVariant.weight!!,
        ChangeField.VARIANT_WEIGHT
    )?.let { UpdateVariantItem(product, variant, it.message, it.action) }

    private fun prepareUpdateVariantOptionValue(
        product: ShopifyProduct,
        variant: ShopifyProductVariant,
        artooVariation: ArtooMappedVariation
    ) = when {
        artooVariation.isDefaultVariant -> null
        else -> change(variant.options[0]::value, artooVariation.name, ChangeField.OPTION_VALUE, variant.options[0].name)
            ?.let { UpdateVariantItem(product, variant, it.message, it.action) }
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
        shopifyImageTools.uploadVariantImages(product, variants.values)
        if (product.options[0].linkedMetafield != null) {
            shopifyImageTools.generateColorSwatches(product, variants.values, "poli-tubitherm", RectPct.CENTER_20)
        }

        // MISSING: set option id
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

    private inner class UpdateProductItem(
        val product: ShopifyProduct,
        override val message: String,
        val block: Any?.() -> Unit,
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
        val variant: ShopifyProductVariant,
        val id: UUID
    ) : VariantItem() {
        override val title = "${variant.options[0].name} ${variant.options[0].value}"
        override val message = "$title entfernt"
    }

    private inner class CreateVariantItem(
        val product: ShopifyProduct,
        val variant: UnsavedShopifyProductVariant,
        val id: UUID
    ) : VariantItem() {
        override val title = "${variant.options[0].name} ${variant.options[0].value}"
        override val message = "$title hinzugefügt"
    }

    private inner class UpdateVariantItem(
        val product: ShopifyProduct,
        val variant: ShopifyProductVariant,
        override val message: String,
        val block: Any?.() -> Unit
    ) : VariantItem() {
        override val title = "${variant.options[0].name} ${variant.options[0].value}"
    }
}