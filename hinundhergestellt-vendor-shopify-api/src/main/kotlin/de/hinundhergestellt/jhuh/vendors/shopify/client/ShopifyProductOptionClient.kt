package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOptionsDeletePayload
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("shopify.read-only", havingValue = "false", matchIfMissing = true)
class ShopifyProductOptionClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    suspend fun delete(product: ShopifyProduct, options: List<ShopifyProductOption>) {
        val request = buildMutation {
            productOptionsDelete(productId = product.id, options = options.map { it.id }) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, ProductOptionsDeletePayload::userErrors)
    }
}