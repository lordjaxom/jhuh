package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildQuery
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Collection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.CollectionConnection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.CollectionReorderProductsPayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MoveInput
import org.springframework.stereotype.Component

@Component
class ShopifyCollectionClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    suspend fun fetchAll(query: String? = null): List<Collection> {
        val request = buildQuery {
            collections(first = 50, query = query) {
                edges {
                    node {
                        id; handle; title; descriptionHtml; sortOrder
                        seo { title; description }
                        products(first = 250) {
                            edges { node { id } }
                            pageInfo { hasNextPage }
                        }
                    }
                }
                pageInfo { hasNextPage }
            }
        }

        val payload = shopifyGraphQLClient.executeQuery<CollectionConnection>(request)
        require(!payload.pageInfo.hasNextPage) { "Pagination not implemented for collections!" }
        return payload.edges
            .map { it.node }
            .onEach { require(!it.products.pageInfo.hasNextPage) { "Pagination not implemented for collections!" } }
    }

    suspend fun reorder(collection: Collection, moves: List<MoveInput>) {
        val request = buildMutation {
            collectionReorderProducts(collection.id, moves) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, CollectionReorderProductsPayload::userErrors)
    }
}