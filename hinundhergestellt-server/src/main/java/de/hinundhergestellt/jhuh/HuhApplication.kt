package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.annotation.JsonInclude
import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.HttpResponse
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequestMixin
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestMixin
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductBase
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductBaseMixin
import org.openapitools.client.infrastructure.Serializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient

@SpringBootApplication
@EnableAsync
@EnableScheduling
class HuhApplication {

    @Bean
    fun ready2orderWebClient(
        @Value("\${ready2order.apikey}") apikey: String
    ): WebClient {
        Serializer.jacksonObjectMapper.apply {
            addMixIn(ProductsIdPutRequest::class.java, ProductsIdPutRequestMixin::class.java)
            addMixIn(ProductsPostRequest::class.java, ProductsPostRequestMixin::class.java)
            addMixIn(ProductsPostRequestProductBase::class.java, ProductsPostRequestProductBaseMixin::class.java)
            setSerializationInclusion(JsonInclude.Include.ALWAYS)
        }

        return WebClient.builder()
            .baseUrl("https://api.ready2order.com/v1")
            .defaultHeader("Authorization", "Bearer $apikey")
            .codecs { it.defaultCodecs().maxInMemorySize(5 * 1024 * 1024) }
            .build()
    }

    @Bean
    fun shopifyApiClient(
        @Value("\${shopify.domain}") domain: String,
        @Value("\${shopify.token}") token: String
    ): GraphQLClient {
        val baseUrl = "https://$domain.myshopify.com/admin/api/2025-04/graphql.json"
        val restTemplate = RestTemplate()
        return GraphQLClient.createCustom(baseUrl) { url, headers, body ->
            val httpHeaders = HttpHeaders()
            httpHeaders.putAll(headers)
            httpHeaders.add("X-Shopify-Access-Token", token)

            val response = restTemplate.exchange(url, HttpMethod.POST, HttpEntity<String>(body, httpHeaders), String::class.java)
            HttpResponse(response.statusCode.value(), response.getBody())
        }
    }
}

fun main(args: Array<String>) {
    runApplication<HuhApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}