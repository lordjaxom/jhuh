package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetafieldsDeletePayload
import org.springframework.stereotype.Component

@Component
class ShopifyMetafieldsClient(
    private val apiClient: GraphQLClient
) {
    fun delete(product: ShopifyProduct, metafields: List<ShopifyMetafield>) {
        val request = buildMutation {
            metafieldsDelete(metafields.map { it.toMetafieldIdentifierInput(product.id)}) {
                userErrors { message; field }
            }
        }
        apiClient.executeMutation(request, MetafieldsDeletePayload::userErrors)
    }
}