package de.hinundhergestellt.jhuh.vendors.shopify

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class ShopifyConfiguration {

    @Bean
    fun shopifyGraphQLClient(
        builder: WebClient.Builder,
        @Value("\${shopify.domain}") domain: String,
        @Value("\${shopify.token}") token: String
    ): WebClientGraphQLClient {
        val webClient = builder
            .baseUrl("https://$domain.myshopify.com/admin/api/2025-04/graphql.json")
            .defaultHeader("X-Shopify-Access-Token", token)
            .build()
        return WebClientGraphQLClient(webClient)
    }
}