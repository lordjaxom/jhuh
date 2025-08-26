package de.hinundhergestellt.jhuh.vendors.ready2order

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.addMixIn
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.RateLimitEnforcingFilter
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequestMixin
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestMixin
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductBase
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductBaseMixin
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class ArtooConfiguration {

    @Bean(defaultCandidate = false)
    fun ready2orderWebClient(
        builder: WebClient.Builder,
        @Value("\${ready2order.apikey}") apikey: String
    ): WebClient {
        val objectMapper = ObjectMapper()
            .addMixIn<ProductsIdPutRequest, ProductsIdPutRequestMixin>()
            .addMixIn<ProductsPostRequest, ProductsPostRequestMixin>()
            .addMixIn<ProductsPostRequestProductBase, ProductsPostRequestProductBaseMixin>()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)

        return builder
            .baseUrl("https://api.ready2order.com/v1")
            .defaultHeader("Authorization", "Bearer $apikey")
            .codecs {
                it.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON))
                it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(objectMapper, MediaType.APPLICATION_JSON))
            }
            .filter(RateLimitEnforcingFilter())
            .build()
    }
}