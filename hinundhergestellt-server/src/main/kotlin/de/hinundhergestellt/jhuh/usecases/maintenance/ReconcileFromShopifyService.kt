package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.spring.annotation.UIScope
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategory
import de.hinundhergestellt.jhuh.backend.syncdb.SyncCategoryRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedCategory
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.streams.asSequence

@Service
@UIScope
class ReconcileFromShopifyService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val syncCategoryRepository: SyncCategoryRepository,
    private val syncVendorRepository: SyncVendorRepository
) {

    @Transactional
    fun synchronize(report: suspend (String) -> Unit) = runBlocking {
        report("Shopify-Produktkatalog aktualisieren...")
        shopifyDataStore.withLockAndRefresh {
            report("Shopify-Produkte mit Datenbank abgleichen...")
            shopifyDataStore.products.forEach { reconcileProduct(it) }

            report("Unbekannte Kategorien abgleichen...")
            artooDataStore.rootCategories.forEach { reconcileCategories(it) }
        }
    }

    private fun reconcileProduct(shopifyProduct: ShopifyProduct) {
        val syncProduct = syncProductRepository.findByShopifyId(shopifyProduct.id) ?: shopifyProduct.toSyncProduct()
        shopifyProduct.variants.forEach { reconcileFromShopify(it, syncProduct) }
        syncProductRepository.save(syncProduct) // for later retrieval in same transaction
    }

    private fun reconcileFromShopify(shopifyVariant: ShopifyProductVariant, syncProduct: SyncProduct) {
        val syncVariant = syncVariantRepository.findByShopifyId(shopifyVariant.id)
            ?: shopifyVariant.barcode.takeIf { it.isNotEmpty() }
                ?.let { syncVariantRepository.findByBarcode(it) }
                ?.also { it.shopifyId = shopifyVariant.id }
            ?: shopifyVariant.toSyncVariant(syncProduct)
        require(syncVariant.product === syncProduct) { "SyncVariant.product does not match ShopifyVariant.product" }
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

    private fun ShopifyProduct.toSyncProduct() =
        SyncProduct(
            shopifyId = id,
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
            ?: SyncVendor(this).also { syncVendorRepository.save(it) }
        else null
}
