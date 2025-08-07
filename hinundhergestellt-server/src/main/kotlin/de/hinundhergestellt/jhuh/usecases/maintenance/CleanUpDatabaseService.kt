package de.hinundhergestellt.jhuh.usecases.maintenance

import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariantRepository
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations

@Service
@VaadinSessionScope
class CleanUpDatabaseService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository,
    private val transactionOperations: TransactionOperations
) {
    val items = mutableListOf<CleanUpItem>()

    suspend fun refresh(report: suspend (String) -> Unit) {
        report("Überprüfe Datenbank auf zu bereinigende Einträge...")
        items.clear()
        items += syncProductRepository.findAll().mapNotNull { checkSyncProduct(it) }
        items += syncVariantRepository.findAll().mapNotNull { checkSyncVariant(it) }
    }

    suspend fun cleanUp(items: Set<CleanUpItem>, report: suspend (String) -> Unit) {
        report("Bereinige ausgewählte Einträge in Datenbank...")
        transactionOperations.execute { items.forEach { it.cleanUp() } }
    }

    private fun checkSyncProduct(syncProduct: SyncProduct): CleanUpItem? {
        val shopifyId = syncProduct.shopifyId
        val artooId = syncProduct.artooId
        if (shopifyId == null && artooId == null) {
            return ProductCleanUpItem(syncProduct, "Produkt hat weder Verbindung zu Shopify noch zu ready2order")
        }

        val shopifyProduct = shopifyId?.let { shopifyDataStore.findProductById(it) }
        val artooProduct = artooId?.let { artooDataStore.findProductById(it) }
        if (shopifyId != null && shopifyProduct == null && artooId != null && artooProduct == null) {
            return ProductCleanUpItem(syncProduct, "Produkt ist sowohl in Shopify als auch ready2order nicht mehr vorhanden")
        }
        if (shopifyId != null && shopifyProduct == null) {
            val details = if (artooId == null) "ohne Verbindung zu ready2order" else "(${artooProduct!!.name} in ready2order)"
            return ProductCleanUpItem(syncProduct, "Produkt $details in Shopify nicht mehr vorhanden")
        }
        if (artooId != null && artooProduct == null) {
            val details = if (shopifyId == null) "ohne Verbindung zu Shopify" else "(${shopifyProduct!!.title} in Shopify)"
            return ProductCleanUpItem(syncProduct, "Produkt $details in ready2order nicht mehr vorhanden")
        }
        return null
    }

    private fun checkSyncVariant(syncVariant: SyncVariant): CleanUpItem? {
        if (items.any { it is ProductCleanUpItem && it.syncProduct.id == syncVariant.product.id }) {
            return null
        }
        // TODO: Provide implementation
        return null
    }

    private inner class ProductCleanUpItem(
        val syncProduct: SyncProduct,
        override val message: String
    ) : CleanUpItem {

        override fun cleanUp() {
            syncProductRepository.delete(syncProduct)
        }
    }
}

sealed interface CleanUpItem {
    val message: String
    fun cleanUp()
}
