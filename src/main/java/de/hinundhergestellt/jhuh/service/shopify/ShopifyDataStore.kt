package de.hinundhergestellt.jhuh.service.shopify

import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyVariant
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyVariantClient
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
    private val variantClient: ShopifyVariantClient
) {
    val products by factory.products()

    fun findProductById(id: String) =
        products.find { it.id == id }

    fun deleteProduct(product: ShopifyProduct) {
        productClient.delete(product)
        products.remove(product)
    }

    fun createVariants(product: ShopifyProduct, variants: List<ShopifyVariant>) {
        variantClient.create(product, variants)
        variants.forEach { product.addVariant(it) }
    }

    fun deleteVariants(product: ShopifyProduct, variants: List<ShopifyVariant>) {
        variantClient.delete(product, variants)
        product.removeVariants(variants)
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
