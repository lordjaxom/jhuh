package de.hinundhergestellt.jhuh.vendors.shopify.datastore

import de.hinundhergestellt.jhuh.util.deferredWithRefresh
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toCollection
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ShopifyDataStore(
    private val productClient: ShopifyProductClient,
    private val variantClient: ShopifyProductVariantClient,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor,
    private val applicationCoroutineScope: CoroutineScope,
    @Value("\${shopify.read-only}") private val readOnly: Boolean
) {
    private val productsAsync = deferredWithRefresh(applicationCoroutineScope) { productClient.findAll().toCollection(mutableListOf()) }
    val products by productsAsync

    fun findProductById(id: String) =
        products.find { it.id == id }

    fun refresh() = productsAsync.refresh()

    suspend fun create(product: UnsavedShopifyProduct): ShopifyProduct {
        val created =
            if (!readOnly) productClient.create(product)
            else product.toDryRunShopifyProduct()
        products.add(created)
        return created
    }

    suspend fun update(product: ShopifyProduct) {
        if (!readOnly) {
            productClient.update(product)
        }
    }

    suspend fun delete(product: ShopifyProduct) {
        if (!readOnly) {
            productClient.delete(product)
        }
        products.remove(product)
    }

    suspend fun create(product: ShopifyProduct, variants: List<UnsavedShopifyProductVariant>) {
        val created =
            if (!readOnly) variantClient.create(product, variants)
            else variants.map { it.toDryRunShopifyProductVariant() }
        product.variants.addAll(created)
    }

    suspend fun update(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        if (!readOnly) {
            variantClient.update(product, variants)
        }
    }

    suspend fun delete(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        if (!readOnly) {
            variantClient.delete(product, variants)
        }
        product.variants.removeAll(variants)
    }
}

val ShopifyProduct.isDryRun get() = id.startsWith("uid://")

private fun UnsavedShopifyProduct.toDryRunShopifyProduct() =
    ShopifyProduct(
        this,
        "uid://${UUID.randomUUID()}",
        options.asSequence().map { it.toDryRunShopifyProductOption() }.toMutableList()
    )

private fun UnsavedShopifyProductOption.toDryRunShopifyProductOption() =
    ShopifyProductOption(this, "uid://${UUID.randomUUID()}")

private fun UnsavedShopifyProductVariant.toDryRunShopifyProductVariant() =
    ShopifyProductVariant(
        this,
        "uid://${UUID.randomUUID()}",
        options.firstOrNull()?.value ?: "Default Title"
    )