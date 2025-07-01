package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileUpdatePayload
import org.springframework.stereotype.Component

@Component
class ShopifyMediaClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    suspend fun update(medias: List<ShopifyMedia>) {
        val request = buildMutation {
            fileUpdate(medias.map { it.toFileUpdateInput() }) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, FileUpdatePayload::userErrors)
    }
}