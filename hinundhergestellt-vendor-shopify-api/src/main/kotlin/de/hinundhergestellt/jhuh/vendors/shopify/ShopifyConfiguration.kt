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
        @Value("\${shopify.domain}") domain: String,
        @Value("\${shopify.token}") token: String
    ): WebClientGraphQLClient {
        val webClient = WebClient.builder()
            .baseUrl("https://$domain.myshopify.com/admin/api/2025-04/graphql.json")
            .defaultHeader("X-Shopify-Access-Token", token)
            .codecs { it.defaultCodecs().maxInMemorySize(5 * 1024 * 1024) }
            .build()
        return WebClientGraphQLClient(webClient)
    }
}