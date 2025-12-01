package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.mapping.ChangeField
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.mapping.ifChanged
import de.hinundhergestellt.jhuh.backend.mapping.toQuotedString
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategory
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncTechnicalDetail
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.tools.ShopifyImageTools
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedCategory
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.taxonomy.ShopifyCategoryTaxonomyProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import kotlin.reflect.KMutableProperty1
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger { }

private typealias ProductProperty<T> = KMutableProperty1<SyncProduct, T>

@Service
@VaadinSessionScope
class ReconcileFromShopifyService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val mappingService: MappingService,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val syncVendorRepository: SyncVendorRepository,
    private val shopifyImageTools: ShopifyImageTools,
    private val transactionOperations: TransactionOperations
) {
    val items = mutableListOf<Item>()

    init {
        items += shopifyDataStore.products.map { reconcile(it) }.flatten()
    }

    suspend fun reload(report: suspend (String) -> Unit) {
        report("Aktualisiere Shopify-Produktkatalog...")
        shopifyDataStore.refreshAndAwait()

        rebuild(report)
    }

    suspend fun rebuild(report: suspend (String) -> Unit) {
        report("Gleiche Shopify-Produkte mit Datenbank ab...")
        items.clear()
        items += shopifyDataStore.products.map { reconcile(it) }.flatten()
    }

    suspend fun apply(items: Set<Item>, report: suspend (String) -> Unit) {
        report("Übernehme markierte Änderungen in Datenbank...")

        val repositoryUpdates = mutableListOf<() -> Unit>()
        items.forEach { item ->
            when (item) {
                is ImmediateProductItem -> item.block()

                is UpdateSyncProductItem -> repositoryUpdates += {
                    syncProductRepository.save(syncProductRepository.findById(item.product.id).orElseThrow().apply(item.block))
                }

                is UpdateSyncVariantItem -> repositoryUpdates += {
                    syncVariantRepository.save(syncVariantRepository.findById(item.variant.id).orElseThrow().apply(item.block))
                }
            }
        }

        transactionOperations.execute { repositoryUpdates.forEach { it() } }
    }

    private fun reconcile(product: ShopifyProduct) = buildList {
        val syncProduct = syncProductRepository.findByShopifyId(product.id) ?: run {
            val matchingArtooProducts = artooDataStore.findProductsByBarcodes(product.variants.map { it.barcode }).toList()
            if (matchingArtooProducts.isEmpty()) {
                logger.info { "ShopifyProduct ${product.title} does not match any ArtooProduct, skip reconciliation" }
                return@buildList
            }
            require(matchingArtooProducts.size == 1) { "More than one ArtooProduct matches barcodes of ShopifyProduct" }

            syncProductRepository.findByArtooId(matchingArtooProducts[0].id)
                ?.also {
                    // TODO: clear handling of add and update of shopifyId
                    add(UpdateSyncProductItem(it, product.title, "Produktzuordnung (Shopify-ID) geändert") { shopifyId = product.id })
                }
                ?: product.toSyncProduct(matchingArtooProducts[0].id)
        }

        addAll(
            listOfNotNull(
                checkReconcileProductProperty(syncProduct, product.title, SyncProduct::urlHandle, product.handle),
                checkReconcileProductProperty(syncProduct, product.title, SyncProduct::type, product.productType),
                checkReconcileProductProperty(syncProduct, product.title, SyncProduct::descriptionHtml, product.descriptionHtml),
                checkReconcileProductProperty(syncProduct, product.title, SyncProduct::seoTitle, product.seoTitle),
                checkReconcileProductProperty(syncProduct, product.title, SyncProduct::metaDescription, product.seoDescription),
                checkReconcileProductTags(syncProduct, product),
                checkReconcileProductTechnicalDetails(syncProduct, product),
                checkReconcileProductCategory(syncProduct, product),
            )
        )
        if (!product.hasOnlyDefaultVariant) {
            checkReconcileProductProperty(syncProduct, product.title, SyncProduct::optionName, product.options[0].name)?.let { add(it) }
        }
        addAll(checkReconcileProductImages(product))
        product.variants.forEach { addAll(reconcile(product, it, syncProduct)) }
    }

    private fun reconcile(product: ShopifyProduct, variant: ShopifyProductVariant, syncProduct: SyncProduct) = buildList {
        val syncVariant = syncVariantRepository.findByShopifyId(variant.id) ?: run {
            val artooVariation = artooDataStore.findVariationByBarcode(variant.barcode)
            if (artooVariation == null) {
                logger.info { "ShopifyProductVariant ${variant.title} does not match any ArtooVariation, skip reconciliation" }
                return@buildList
            }

            syncVariantRepository.findByArtooId(artooVariation.id)
                ?.also {
                    // TODO: clear handling of add and update of shopifyId
                    add(UpdateSyncVariantItem(it, "${product.title} (${variant.title})", "Variantenzuordnung ergänzen") {
                        shopifyId = variant.id
                    })
                }
                ?: variant.toSyncVariant(syncProduct)
        }
        require(syncVariant.product.id == syncProduct.id) { "SyncVariant.product does not match ShopifyVariant.product" }

        ifChanged(variant.weight, syncVariant.weight, ChangeField.VARIANT_WEIGHT) {
            add(UpdateSyncVariantItem(syncVariant, "${product.title} (${variant.title})", it) { weight = variant.weight })
        }
    }

    private fun <T> checkReconcileProductProperty(product: SyncProduct, title: String, property: ProductProperty<T>, newValue: T) =
        ifChanged(property.get(product), newValue, property.name) {
            UpdateSyncProductItem(product, title, it) { property.set(this, newValue) }
        }

    private fun checkReconcileProductTags(
        syncProduct: SyncProduct,
        shopifyProduct: ShopifyProduct
    ): UpdateSyncProductItem? {
        val shopifyTags = shopifyProduct.tags - mappingService.inheritedTags(syncProduct)
        if (syncProduct.tags == shopifyTags) return null

        val addedTags = shopifyTags - syncProduct.tags
        val removedTags = syncProduct.tags - shopifyTags
        val message = "Tags" +
                (if (addedTags.isNotEmpty()) " ${addedTags.toQuotedString()} hinzugefügt" else "") +
                (if (addedTags.isNotEmpty() && removedTags.isNotEmpty()) "," else "") +
                (if (removedTags.isNotEmpty()) " ${removedTags.toQuotedString()} entfernt" else "")
        return UpdateSyncProductItem(syncProduct, shopifyProduct.title, message) { tags.clear(); tags += shopifyTags }
    }

    private fun checkReconcileProductTechnicalDetails(
        syncProduct: SyncProduct,
        shopifyProduct: ShopifyProduct
    ): UpdateSyncProductItem? {
        val loadedTechnicalDetails = mappingService.extractTechnicalDetails(shopifyProduct)
        val knownTechnicalDetails = syncProduct.technicalDetails.associate { it.name to it.value }
        if (loadedTechnicalDetails == knownTechnicalDetails) return null
        return UpdateSyncProductItem(syncProduct, shopifyProduct.title, "Technische Daten geändert") {
            technicalDetails.clear()
            technicalDetails += loadedTechnicalDetails.entries
                .mapIndexed { index, (name, value) -> SyncTechnicalDetail(name, value, index) }
        }
    }

    private fun checkReconcileProductCategory(sync: SyncProduct, shopify: ShopifyProduct): UpdateSyncProductItem? {
        val newCategory = shopify.category?.name ?: return null
        val oldCategory = sync.shopifyCategory?.let { ShopifyCategoryTaxonomyProvider.categories[it]!!.name }
        return ifChanged(oldCategory, newCategory, ChangeField.PRODUCT_CATEGORY) {
            UpdateSyncProductItem(sync, shopify.title, it) { shopifyCategory = shopify.category?.id }
        }
    }

    private fun checkReconcileProductImages(product: ShopifyProduct) = buildList {
        shopifyImageTools.unorganizedProductImages(product).takeIf { it.isNotEmpty() }?.let {
            add(ImmediateProductItem(product.title, "${it.size} Produktbilder nicht normalisiert") {
                shopifyImageTools.normalizeImagesToUrlHandle(product)
            })
        }

        shopifyImageTools.locallyMissingProductImages(product).takeIf { it.isNotEmpty() }?.let {
            add(ImmediateProductItem(product.title, "${it.size} Produktbilder lokal nicht vorhanden") {
                shopifyImageTools.downloadLocallyMissingProductImages(product, it)
            })
        }
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

    private fun ShopifyProduct.toSyncProduct(artooId: String) =
        SyncProduct(
            shopifyId = id,
            artooId = artooId,
            vendor = vendor.asSyncVendor(),
            type = productType,
            tags = (tags - listOf(vendor, productType)).toMutableSet(),
            synced = true
        )

    private fun ShopifyProductVariant.toSyncVariant(syncProduct: SyncProduct) =
        SyncVariant(
            product = syncProduct,
            barcode = barcode,
            shopifyId = id
        )

    private fun String.asSyncVendor() =
        // TODO: Untested after splitting from product management
        if (isNotEmpty()) syncVendorRepository.findByNameIgnoreCase(this) ?: SyncVendor(this)
        else null

    sealed interface Item {
        val title: String
        val message: String
    }

    sealed interface ProductItem : Item

    private inner class ImmediateProductItem(
        override val title: String,
        override val message: String,
        val block: suspend () -> Unit
    ) : ProductItem

    private inner class UpdateSyncProductItem(
        val product: SyncProduct,
        override val title: String,
        override val message: String,
        val block: SyncProduct.() -> Unit
    ) : ProductItem

    sealed interface VariantItem : Item

    private inner class UpdateSyncVariantItem(
        val variant: SyncVariant,
        override val title: String,
        override val message: String,
        val block: SyncVariant.() -> Unit
    ) : VariantItem
}