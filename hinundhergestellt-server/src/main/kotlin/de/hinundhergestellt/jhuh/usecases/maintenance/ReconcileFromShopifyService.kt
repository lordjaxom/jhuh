package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategory
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncTechnicalDetail
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedCategory
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.util.UUID
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger { }

@Service
@VaadinSessionScope
class ReconcileFromShopifyService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val syncVendorRepository: SyncVendorRepository,
    private val transactionOperations: TransactionOperations
) {

    val items = mutableListOf<ReconcileItem>()

    suspend fun refresh(report: suspend (String) -> Unit) {
        report("Aktualisiere Shopify-Produktkatalog...")
        shopifyDataStore.refreshAndAwait()

        report("Gleiche Shopify-Produkte mit Datenbank ab...")
        items.clear()
        items += shopifyDataStore.products.asSequence().map { reconcile(it) }.flatten()

//        report("Unbekannte Kategorien abgleichen...")
//        artooDataStore.rootCategories.forEach { reconcileCategories(it) }

//        transactionOperations.execute { syncProductRepository.saveAll(changedSyncProducts) }
    }

    suspend fun apply(items: Set<ReconcileItem>, report: suspend (String) -> Unit) {
        report("Übernehme markierte Änderungen in Datenbank...")

        val changedProducts = LinkedHashMap<UUID, SyncProduct>()
        val changedVariants = LinkedHashMap<UUID, SyncVariant>()
        items.asSequence()
            .map { (it as TypedReconcileItem<*>).reconcile() }
            .forEach {
                when (it) {
                    is SyncProduct -> changedProducts.putIfAbsent(it.id, it)
                    is SyncVariant -> changedVariants.putIfAbsent(it.id, it)
                }
            }

        transactionOperations.execute {
            changedProducts.values.forEach { syncProductRepository.save(it) }
            changedVariants.values.forEach { syncVariantRepository.save(it) }
        }
    }

    private fun reconcile(product: ShopifyProduct) = sequence {
        val syncProduct = syncProductRepository.findByShopifyId(product.id) ?: run {
            val matchingArtooProducts = artooDataStore.findProductsByBarcodes(product.variants.map { it.barcode }).toList()
            if (matchingArtooProducts.isEmpty()) {
                logger.info { "ShopifyProduct ${product.title} does not match any ArtooProduct, skip reconciliation" }
                return@sequence
            }
            require(matchingArtooProducts.size == 1) { "More than one ArtooProduct matches barcodes of ShopifyProduct" }

            syncProductRepository.findByArtooId(matchingArtooProducts[0].id)
                ?.also { it.shopifyId = product.id }
                ?: product.toSyncProduct(matchingArtooProducts[0].id)
        }

        if (syncProduct.descriptionHtml != product.descriptionHtml) {
            yield(ProductFieldReconcileItem(syncProduct, product.title, "Leere Produktbeschreibung ergänzt") {
                descriptionHtml = product.descriptionHtml
            })
        }

        val loadedTechnicalDetails = extractTechnicalDetails(product)
        val knownTechnicalDetails = syncProduct.technicalDetails.map { it.name to it.value }
        if (loadedTechnicalDetails != null && loadedTechnicalDetails != knownTechnicalDetails) {
            yield(ProductFieldReconcileItem(syncProduct, product.title, "Technische Daten geändert") {
                technicalDetails.clear()
                technicalDetails += loadedTechnicalDetails.mapIndexed { index, (name, value) -> SyncTechnicalDetail(name, value, index) }
            })
        }

        yieldAll(product.variants.asSequence().flatMap { reconcile(product, it, syncProduct) })
    }

    private fun reconcile(product: ShopifyProduct, variant: ShopifyProductVariant, syncProduct: SyncProduct) = sequence {
        val syncVariant = syncVariantRepository.findByShopifyId(variant.id) ?: run {
            val artooVariation = artooDataStore.findVariationByBarcode(variant.barcode)
            if (artooVariation == null) {
                logger.info { "ShopifyProductVariant ${variant.title} does not match any ArtooVariation, skip reconciliation" }
                return@sequence
            }
            syncVariantRepository.findByArtooId(artooVariation.id)
                ?.also { it.shopifyId = variant.id }
                ?: variant.toSyncVariant(syncProduct)
        }
        require(syncVariant.product.id == syncProduct.id) { "SyncVariant.product does not match ShopifyVariant.product" }

        require(variant.weight.unit == WeightUnit.GRAMS) { "Only GRAMS are supported at this time" }
        val loadedWeight = variant.weight.value
        if (loadedWeight.compareTo(syncVariant.weight) != 0) {
            yield(
                VariantFieldReconcileItem(
                    syncVariant,
                    "${product.title} (${variant.title})",
                    "Gewicht von ${syncVariant.weight ?: "leer"} auf ${loadedWeight} geändert",
                    { weight = loadedWeight }
                )
            )
        }
    }

    private fun extractTechnicalDetails(product: ShopifyProduct): List<Pair<String, String>>? {
        return product.metafields
            .find { it.namespace == "custom" && it.key == "product_specs" }
            ?.let { extractTechnicalDetails(it) }
    }

    private fun extractTechnicalDetails(metafield: ShopifyMetafield): List<Pair<String, String>> {
        return Jsoup.parse(metafield.value)
            .select("table tr")
            .map { it.select("th").text().trim() to it.select("td").text().trim() }
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

    private inner class ProductFieldReconcileItem(
        private val product: SyncProduct,
        override val title: String,
        override val message: String,
        private val action: SyncProduct.() -> Unit
    ) : ProductReconcileItem {

        override fun reconcile() = product.apply(action)
    }

    private inner class VariantFieldReconcileItem(
        private val product: SyncVariant,
        override val title: String,
        override val message: String,
        private val action: SyncVariant.() -> Unit
    ) : VariantReconcileItem {

        override fun reconcile() = product.apply(action)
    }
}

sealed interface ReconcileItem {
    val title: String
    val message: String
}

sealed interface TypedReconcileItem<T> : ReconcileItem {
    fun reconcile(): T
}

sealed interface ProductReconcileItem : TypedReconcileItem<SyncProduct>
sealed interface VariantReconcileItem : TypedReconcileItem<SyncVariant>