package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.UUID

@Configuration
@ConditionalOnProperty("shopify.read-only", havingValue = "true")
class ReadOnlyShopifyClients(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    @Bean
    fun shopifyMetafieldClient() =
        object : ShopifyMetafieldClient(shopifyGraphQLClient) {
            override suspend fun delete(product: ShopifyProduct, metafields: List<ShopifyMetafield>) {}
        }

    @Bean
    fun shopifyProductClient() =
        object : ShopifyProductClient(shopifyGraphQLClient) {
            override suspend fun create(product: UnsavedShopifyProduct) = product.toDryRunShopifyProduct()
            override suspend fun update(product: ShopifyProduct) {}
            override suspend fun delete(product: ShopifyProduct) {}
        }

    @Bean
    fun shopifyProductOptionClient() =
        object : ShopifyProductOptionClient(shopifyGraphQLClient) {
            override suspend fun delete(product: ShopifyProduct, options: List<ShopifyProductOption>) {}
        }

    @Bean
    fun shopifyProductVariantClient() =
        object : ShopifyProductVariantClient(shopifyGraphQLClient) {
            override suspend fun create(product: ShopifyProduct, variants: List<UnsavedShopifyProductVariant>) =
                variants.map { it.toDryRunShopifyProductVariant() }

            override suspend fun update(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {}
            override suspend fun delete(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {}
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