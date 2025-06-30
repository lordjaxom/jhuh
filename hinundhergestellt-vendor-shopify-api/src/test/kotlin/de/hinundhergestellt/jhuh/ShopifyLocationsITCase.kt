package de.hinundhergestellt.jhuh

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.executeQuery
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildQuery
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.LocationConnection
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest
class ShopifyLocationsITCase {

    @Autowired
    private lateinit var shopifyGraphQLClient: WebClientGraphQLClient

    @Test
    fun fetchesLocations() = runBlocking {
        val request = buildQuery {
            locations(first = 10) {
                edges {
                    node {
                        fulfillsOnlineOrders; hasActiveInventory; id; isPrimary; shipsInventory
                    }
                }
            }
        }

        val payload = shopifyGraphQLClient.executeQuery<LocationConnection>(request)
        println(payload)
    }
}