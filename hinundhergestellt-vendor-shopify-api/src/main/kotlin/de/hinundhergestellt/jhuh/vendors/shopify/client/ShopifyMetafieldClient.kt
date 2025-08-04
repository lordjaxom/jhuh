package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MetafieldsDeletePayload
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("shopify.read-only", havingValue = "false", matchIfMissing = true)
class ShopifyMetafieldClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    suspend fun delete(product: ShopifyProduct, metafields: List<ShopifyMetafield>) {
        val request = buildMutation {
            metafieldsDelete(metafields.map { it.toMetafieldIdentifierInput(product.id)}) {
                userErrors { message; field }
            }
        }
        shopifyGraphQLClient.executeMutation(request, MetafieldsDeletePayload::userErrors)
    }
}