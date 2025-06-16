package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkCreatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkCreateStrategy
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkDeletePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkUpdatePayload
import org.springframework.stereotype.Component

@Component
class ShopifyProductVariantClient(
    private val apiClient: GraphQLClient
) {
    fun create(product: ShopifyProduct, variants: List<UnsavedShopifyProductVariant>): List<ShopifyProductVariant> {
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

        val payload = apiClient.executeMutation(request, ProductVariantsBulkCreatePayload::userErrors)
        return variants
            .zip(payload.productVariants!!)
            .map { (variant, created) -> ShopifyProductVariant(variant, created.id, created.title) }
    }

    fun update(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        val request = buildMutation {
            productVariantsBulkUpdate(productId = product.id, variants = variants.map { it.toProductVariantsBulkInput() }) {
                userErrors { message; field }
            }
        }

        apiClient.executeMutation(request, ProductVariantsBulkUpdatePayload::userErrors)
    }

    fun delete(product: ShopifyProduct, variants: List<ShopifyProductVariant>) {
        val request = buildMutation {
            productVariantsBulkDelete(productId = product.id, variantsIds = variants.map { it.id }) {
                userErrors { message;field }
            }
        }

        apiClient.executeMutation(request, ProductVariantsBulkDeletePayload::userErrors)
    }
}