package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildQuery
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.LocationConnection
import org.springframework.stereotype.Component

@Component
class ShopifyLocationClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    suspend fun fetchAll(): List<ShopifyLocation> {
        val request = buildQuery {
            locations(first = 10) {
                edges {
                    node {
                        id; name; isPrimary; fulfillsOnlineOrders; hasActiveInventory; shipsInventory
                    }
                }
            }
        }

        val payload = shopifyGraphQLClient.executeQuery<LocationConnection>(request)
        return payload.edges.map { ShopifyLocation(it.node) }
    }
}