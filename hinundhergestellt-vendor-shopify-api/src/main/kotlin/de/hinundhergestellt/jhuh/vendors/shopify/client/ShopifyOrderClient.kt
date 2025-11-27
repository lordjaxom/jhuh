package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildQuery
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Order
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OrderConnection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OrderSortKeys
import org.springframework.stereotype.Component

@Component
class ShopifyOrderClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    suspend fun fetchAll(): List<Order> {
        val request = buildQuery {
            orders(first = 250, sortKey = OrderSortKeys.CREATED_AT, reverse = true) {
                edges {
                    node {
                        lineItems(first = 250) {
                            edges {
                                node {
                                    product { id }
                                    variant { id }
                                    quantity
                                }
                            }
                            pageInfo { hasNextPage }
                        }
                    }
                }
                pageInfo { hasNextPage }
            }
        }

        val payload = shopifyGraphQLClient.executeQuery<OrderConnection>(request)
        require(!payload.pageInfo.hasNextPage) { "Pagination not implemented for orders!" }
        return payload.edges
            .map { it.node }
            .onEach { require(!it.lineItems.pageInfo.hasNextPage) { "Pagination not implemented for orders!" } }
    }
}