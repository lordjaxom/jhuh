package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.HttpResponse
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.ApiClient
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.RateLimitEnforcingApiClient
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequestMixin
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestMixin
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.web.client.RestTemplate

@SpringBootApplication
@EnableAsync
class HuhApplication {

    @Bean
    fun ready2orderApiClient(
        @Value("\${ready2order.apikey}") apikey: String
    ): ApiClient {
        val objectMapper = ObjectMapper()
        objectMapper.addMixIn(ProductsIdPutRequest::class.java, ProductsIdPutRequestMixin::class.java)
        objectMapper.addMixIn(ProductsPostRequest::class.java, ProductsPostRequestMixin::class.java)
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS)

        val messageConverter = MappingJackson2HttpMessageConverter(objectMapper)
        val restTemplate = RestTemplate(listOf(messageConverter))

        val apiClient = RateLimitEnforcingApiClient(restTemplate)
        apiClient.setBasePath("https://api.ready2order.com/v1")
        apiClient.setApiKey(apikey)
        return apiClient
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