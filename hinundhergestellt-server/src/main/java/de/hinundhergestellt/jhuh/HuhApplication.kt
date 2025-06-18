package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.RateLimitEnforcingFilter
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequestMixin
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestMixin
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductBase
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductBaseMixin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient

@SpringBootApplication
@EnableAsync
@EnableScheduling
class HuhApplication {

    @Bean
    fun ready2orderWebClient(
        @Value("\${ready2order.apikey}") apikey: String
    ): WebClient {
        val objectMapper = ObjectMapper()
        objectMapper.addMixIn(ProductsIdPutRequest::class.java, ProductsIdPutRequestMixin::class.java)
        objectMapper.addMixIn(ProductsPostRequest::class.java, ProductsPostRequestMixin::class.java)
        objectMapper.addMixIn(ProductsPostRequestProductBase::class.java, ProductsPostRequestProductBaseMixin::class.java)
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS)

        return WebClient.builder()
            .baseUrl("https://api.ready2order.com/v1")
            .defaultHeader("Authorization", "Bearer $apikey")
            .codecs {
                it.defaultCodecs().maxInMemorySize(5 * 1024 * 1024)
                it.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON))
                it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON))
            }
            .filter(RateLimitEnforcingFilter())
            .build()
    }

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

    @Bean // TODO: Cancellation
    fun applicationScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

fun main(args: Array<String>) {
    runApplication<HuhApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}