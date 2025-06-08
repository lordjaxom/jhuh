package de.hinundhergestellt.jhuh.vendors.shopify.datastore

import de.hinundhergestellt.jhuh.util.asyncWithRefresh
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProductVariant
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
    @Value("\${shopify.read-only}") private val readOnly: Boolean
) {
    private val productsAsync = asyncWithRefresh(taskExecutor) { productClient.findAll().toMutableList() }
    val products by productsAsync

    fun findProductById(id: String) =
        products.find { it.id == id }

    fun refresh() = productsAsync.refresh()

    fun create(product: UnsavedShopifyProduct): ShopifyProduct {
        val created =
            if (!readOnly) productClient.create(product)
            else ShopifyProduct(
                product,
                "uid://${UUID.randomUUID()}",
                product.options.map { ShopifyProductOption(it, "uid://${UUID.randomUUID()}") }
            )
        products.add(created)
        return created
    }

    fun update(product: ShopifyProduct) {
        if (!readOnly) {
            productClient.update(product)
        }
    }

    fun delete(product: ShopifyProduct) {
        if (!readOnly) {
            productClient.delete(product)
        }
        products.remove(product)
    }

    fun create(product: ShopifyProduct, variants: List<UnsavedShopifyProductVariant>) {
        val created =
            if (!readOnly) variantClient.create(product, variants)
            else variants.map { ShopifyProductVariant(it, "uid://${UUID.randomUUID()}", it.options[0].value) }
        product.variants.addAll(created)
    }

    fun update(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        if (!readOnly) {
            variantClient.update(product, variants)
        }
    }

    fun delete(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        if (!readOnly) {
            variantClient.delete(product, variants)
        }
        product.variants.removeAll(variants)
    }
}