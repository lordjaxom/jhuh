package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkCreatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkCreateStrategy
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkDeletePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkUpdatePayload
import org.springframework.stereotype.Component

@Component
class ShopifyProductVariantClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    suspend fun create(product: ShopifyProduct, variants: List<UnsavedShopifyProductVariant>): List<ShopifyProductVariant> {
        val strategy =
            if (product.variants.isEmpty()) ProductVariantsBulkCreateStrategy.REMOVE_STANDALONE_VARIANT
            else ProductVariantsBulkCreateStrategy.DEFAULT
        val request = buildMutation {
            productVariantsBulkCreate(
                productId = product.id,
                variants = variants.map { it.toProductVariantsBulkInput() },
                strategy = strategy
            ) {
                productVariants { id; title }
                userErrors { message; field }
            }
        }

        val payload = shopifyGraphQLClient.executeMutation(request, ProductVariantsBulkCreatePayload::userErrors)
        return variants
            .zip(payload.productVariants!!)
            .map { (variant, created) -> ShopifyProductVariant(variant, created.id, created.title) }
    }

    suspend fun update(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        val request = buildMutation {
            productVariantsBulkUpdate(productId = product.id, variants = variants.map { it.toProductVariantsBulkInput() }) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, ProductVariantsBulkUpdatePayload::userErrors)
    }

    suspend fun delete(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        val request = buildMutation {
            productVariantsBulkDelete(productId = product.id, variantsIds = variants.map { it.id }) {
                userErrors { message;field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, ProductVariantsBulkDeletePayload::userErrors)
    }
}