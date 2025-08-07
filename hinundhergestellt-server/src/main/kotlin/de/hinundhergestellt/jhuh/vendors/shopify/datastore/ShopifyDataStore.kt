package de.hinundhergestellt.jhuh.vendors.shopify.datastore

import de.hinundhergestellt.jhuh.core.deferredWithRefresh
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyLocationClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Service
import java.util.concurrent.CopyOnWriteArrayList

@Service
class ShopifyDataStore(
    private val productClient: ShopifyProductClient,
    private val variantClient: ShopifyProductVariantClient,
    private val locationClient: ShopifyLocationClient,
    applicationCoroutineScope: CoroutineScope
) {
    private val productsDeferred = deferredWithRefresh(applicationCoroutineScope) { fetchProducts() }
    private val locationDeferred = deferredWithRefresh(applicationCoroutineScope) { fetchPrimaryLocation() }

    val products by productsDeferred
    val location by locationDeferred

    private val lock = Mutex()

    fun findProductById(id: String) =
        products.find { it.id == id }

    fun findVariantById(id: String) =
        products.firstNotNullOfOrNull { product -> product.variants.find { it.id == id } }

    suspend fun refreshAndAwait() {
        productsDeferred.refreshAndAwait()
    }

    suspend fun withLockAndRefresh(block: suspend () -> Unit) {
        lock.withLock {
            refreshAndAwait()
            block()
        }
    }

    suspend fun create(product: UnsavedShopifyProduct): ShopifyProduct {
        requireLock()
        val created = productClient.create(product)
        products.add(created)
        return created
    }

    suspend fun update(product: ShopifyProduct) {
        requireLock()
        productClient.update(product)
    }

    suspend fun delete(product: ShopifyProduct) {
        requireLock()
        productClient.delete(product)
        products.remove(product)
    }

    suspend fun create(product: ShopifyProduct, variants: List<UnsavedShopifyProductVariant>) {
        requireLock()
        val created = variantClient.create(product, variants)
        product.variants.addAll(created)
    }

    suspend fun update(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        requireLock()
        variantClient.update(product, variants)
    }

    suspend fun delete(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        requireLock()
        variantClient.delete(product, variants)
        product.variants.removeAll(variants)
    }

    private suspend fun fetchProducts() = CopyOnWriteArrayList(productClient.fetchAll().toList())
    private suspend fun fetchPrimaryLocation() = locationClient.fetchAll().first { it.isPrimary }

    private fun requireLock() = require(lock.isLocked) { "Write operations require a lock" }
}