package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.spring.annotation.UIScope
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
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyWeight
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.math.BigDecimal
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger { }

@Service
@UIScope
class ReconcileFromShopifyService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val syncVendorRepository: SyncVendorRepository,
    private val transactionOperations: TransactionOperations
) {

    suspend fun synchronize(report: suspend (String) -> Unit) {
        report("Shopify-Produktkatalog aktualisieren...")
        shopifyDataStore.refreshAndAwait()

        report("Shopify-Produkte mit Datenbank abgleichen...")
        val changedSyncProducts = shopifyDataStore.products.mapNotNull { reconcileProduct(it) }

//        report("Unbekannte Kategorien abgleichen...")
//        artooDataStore.rootCategories.forEach { reconcileCategories(it) }

        transactionOperations.execute { syncProductRepository.saveAll(changedSyncProducts) }
    }

    private fun reconcileProduct(shopifyProduct: ShopifyProduct): SyncProduct? {
        val syncProduct = syncProductRepository.findByShopifyId(shopifyProduct.id) ?: run {
            val matchingArtooProducts = artooDataStore.findProductsByBarcodes(shopifyProduct.variants.map { it.barcode }).toList()
            if (matchingArtooProducts.isEmpty()) {
                logger.info { "ShopifyProduct ${shopifyProduct.title} does not match any ArtooProduct, skip reconciliation" }
                return null
            }
            require(matchingArtooProducts.size == 1) { "More than one ArtooProduct matches barcodes of ShopifyProduct" }

            syncProductRepository.findByArtooId(matchingArtooProducts[0].id)
                ?.also { it.shopifyId = shopifyProduct.id }
                ?: shopifyProduct.toSyncProduct(matchingArtooProducts[0].id)
        }

        syncProduct.descriptionHtml = shopifyProduct.descriptionHtml
        if (syncProduct.technicalDetails.isEmpty()) {
            shopifyProduct.metafields
                .find { it.namespace == "custom" && it.key == "product_specs" }
                ?.let { metafield ->
                    Jsoup.parse(metafield.value)
                        .select("table tr")
                        .map { it.select("th").text().trim() to it.select("td").text().trim() }
                }
                ?.forEachIndexed { index, (key, value) -> syncProduct.technicalDetails.add(SyncTechnicalDetail(key, value, index)) }
        }

        shopifyProduct.variants.forEach { reconcileFromShopify(it, syncProduct) }
        return syncProduct
    }

    private fun reconcileFromShopify(shopifyVariant: ShopifyProductVariant, syncProduct: SyncProduct) {
        val syncVariant = syncVariantRepository.findByShopifyId(shopifyVariant.id) ?: run {
            val artooVariation = artooDataStore.findVariationByBarcode(shopifyVariant.barcode)
            if (artooVariation == null) {
                logger.info { "ShopifyProductVariant ${shopifyVariant.title} does not match any ArtooVariation, skip reconciliation" }
                return
            }
            syncVariantRepository.findByArtooId(artooVariation.id)
                ?.also { it.shopifyId = shopifyVariant.id }
                ?: shopifyVariant.toSyncVariant(syncProduct)
        }
        require(syncVariant.product.id == syncProduct.id) { "SyncVariant.product does not match ShopifyVariant.product" }

        syncVariant.weight = shopifyVariant.weight.toBigDecimal()
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
        ).also { syncProduct.variants.add(it) }

    private fun String.asSyncVendor() =
        if (isNotEmpty()) syncVendorRepository.findByNameIgnoreCase(this)
            ?: SyncVendor(this) // .also { syncVendorRepository.save(it) }
        else null
}

private fun ShopifyWeight.toBigDecimal(): BigDecimal {
    require(unit == WeightUnit.GRAMS) { "Only GRAMS are supported at this time" }
    return BigDecimal(value)
}
