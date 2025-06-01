package de.hinundhergestellt.jhuh.service.shopify

import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.UnsavedShopifyProductVariant
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.Scope
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.stereotype.Service
import java.util.concurrent.Callable

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
class ShopifyDataStore(
    factory: ShopifyDataStoreFactory,
    private val productClient: ShopifyProductClient,
    private val variantClient: ShopifyProductVariantClient
) {
    val products by factory.products()

    fun findProductById(id: String) =
        products.find { it.id == id }

    fun create(product: UnsavedShopifyProduct): ShopifyProduct {
        val created = productClient.create(product)
        products.add(created)
        return created
    }

    fun delete(product: ShopifyProduct) {
        productClient.delete(product)
        products.remove(product)
    }

    fun create(product: ShopifyProduct, variants: List<UnsavedShopifyProductVariant>) {
        val created = variantClient.create(product, variants)
        product.variants.addAll(created)
    }

    fun delete(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        variantClient.delete(product, variants)
        product.variants.removeAll(variants)
    }
}

@Service
class ShopifyDataStoreFactory(
    private val productClient: ShopifyProductClient,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor
) {
    fun products(): Lazy<MutableList<ShopifyProduct>> =
        taskExecutor
            .submit(Callable { productClient.findAll().toMutableList() })
            .let { lazy { it.get() } }
}
