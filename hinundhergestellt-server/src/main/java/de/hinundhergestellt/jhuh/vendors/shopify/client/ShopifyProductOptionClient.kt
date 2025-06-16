package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOptionsDeletePayload
import org.springframework.stereotype.Component

@Component
class ShopifyProductOptionClient(
    private val apiClient: GraphQLClient
) {
    fun delete(product: ShopifyProduct, options: List<ShopifyProductOption>) {
        val request = buildMutation {
            productOptionsDelete(productId = product.id, options = options.map { it.id }) {
                userErrors { message; field }
            }
        }

        apiClient.executeMutation(request, ProductOptionsDeletePayload::userErrors)
    }
}