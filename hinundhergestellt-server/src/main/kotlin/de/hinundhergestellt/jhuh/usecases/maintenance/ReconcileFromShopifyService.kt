package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
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
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedCategory
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.data.jpa.repository.JpaRepository
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
    private val mappingService: MappingService,
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
        shopifyDataStore.products.forEach { reconcile(it) }

//        report("Unbekannte Kategorien abgleichen...")
//        artooDataStore.rootCategories.forEach { reconcileCategories(it) }
    }

    suspend fun apply(items: Set<ReconcileItem>, report: suspend (String) -> Unit) {
        report("Übernehme markierte Änderungen in Datenbank...")
        transactionOperations.execute { items.forEach { it.reconcile() } }
    }

    private fun reconcile(product: ShopifyProduct) {
        val syncProduct = syncProductRepository.findByShopifyId(product.id) ?: run {
            val matchingArtooProducts = artooDataStore.findProductsByBarcodes(product.variants.map { it.barcode }).toList()
            if (matchingArtooProducts.isEmpty()) {
                logger.info { "ShopifyProduct ${product.title} does not match any ArtooProduct, skip reconciliation" }
                return
            }
            require(matchingArtooProducts.size == 1) { "More than one ArtooProduct matches barcodes of ShopifyProduct" }

            syncProductRepository.findByArtooId(matchingArtooProducts[0].id)
                ?.also { it.shopifyId = product.id }
                ?: product.toSyncProduct(matchingArtooProducts[0].id)
        }

        if (syncProduct.type != product.productType) {
            val oldText = syncProduct.type.toQuotedString()
            val newText = product.productType.toQuotedString()
            items += ProductReconcileItem(syncProduct, product.title, "Produktart von $oldText zu $newText geändert") {
                type = product.productType
            }
        }

        val inheritedTags = mappingService.inheritedTags(syncProduct)
        val directTags = product.tags - inheritedTags
        if (syncProduct.tags != directTags) {
            val oldText = syncProduct.tags.toQuotedString()
            val newText = directTags.toQuotedString()
            items += ProductReconcileItem(syncProduct, product.title, "Tags von $oldText zu $newText geändert") {
                tags.clear()
                tags += directTags
            }
        }

        if (syncProduct.descriptionHtml != product.descriptionHtml) {
            items += ProductReconcileItem(syncProduct, product.title, "Produktbeschreibung geändert") {
                descriptionHtml = product.descriptionHtml
            }
        }

        val loadedTechnicalDetails = extractTechnicalDetails(product)
        val knownTechnicalDetails = syncProduct.technicalDetails.map { it.name to it.value }
        if (loadedTechnicalDetails != knownTechnicalDetails) {
            items += ProductReconcileItem(syncProduct, product.title, "Technische Daten geändert") {
                technicalDetails.clear()
                technicalDetails += loadedTechnicalDetails.mapIndexed { index, (name, value) -> SyncTechnicalDetail(name, value, index) }
            }
        }

        product.variants.forEach { reconcile(product, it, syncProduct) }
    }

    private fun reconcile(product: ShopifyProduct, variant: ShopifyProductVariant, syncProduct: SyncProduct) {
        val syncVariant = syncVariantRepository.findByShopifyId(variant.id) ?: run {
            val artooVariation = artooDataStore.findVariationByBarcode(variant.barcode)
            if (artooVariation == null) {
                logger.info { "ShopifyProductVariant ${variant.title} does not match any ArtooVariation, skip reconciliation" }
                return
            }
            syncVariantRepository.findByArtooId(artooVariation.id)
                ?.also { it.shopifyId = variant.id }
                ?: variant.toSyncVariant(syncProduct)
        }
        require(syncVariant.product.id == syncProduct.id) { "SyncVariant.product does not match ShopifyVariant.product" }

        require(variant.weight.unit == WeightUnit.GRAMS) { "Only GRAMS are supported at this time" }
        val loadedWeight = variant.weight.value
        if (syncVariant.weight == null || loadedWeight.compareTo(syncVariant.weight) != 0) {
            items += VariantReconcileItem(
                syncVariant,
                "${product.title} (${variant.title})",
                "Gewicht von ${syncVariant.weight ?: "leer"} auf $loadedWeight geändert",
                { weight = loadedWeight }
            )
        }
    }

    private fun extractTechnicalDetails(product: ShopifyProduct): List<Pair<String, String>> {
        return product.metafields
            .find { it.namespace == "custom" && it.key == "product_specs" }
            ?.let { extractTechnicalDetails(it) }
            ?: listOf()
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

    inner class ProductReconcileItem(
        product: SyncProduct,
        title: String,
        message: String,
        action: SyncProduct.() -> Unit
    ) : TypedReconcileItem<SyncProduct>(syncProductRepository, product.id, title, message, action)

    inner class VariantReconcileItem(
        variant: SyncVariant,
        title: String,
        message: String,
        action: SyncVariant.() -> Unit
    ) : TypedReconcileItem<SyncVariant>(syncVariantRepository, variant.id, title, message, action)
}

sealed interface ReconcileItem {
    val title: String
    val message: String
    fun reconcile()
}

sealed class TypedReconcileItem<T : Any>(
    private val repository: JpaRepository<T, UUID>,
    private val id: UUID,
    override val title: String,
    override val message: String,
    private val action: T.() -> Unit
) : ReconcileItem {

    override fun reconcile() {
        repository.save(repository.findById(id).orElseThrow().apply(action))
    }
}