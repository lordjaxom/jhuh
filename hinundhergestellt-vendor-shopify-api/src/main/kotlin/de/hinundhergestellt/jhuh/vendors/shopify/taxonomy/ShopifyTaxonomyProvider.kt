package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

private val logger = KotlinLogging.logger { }

@Component
class ShopifyTaxonomyProvider(
    private val genericWebClient: WebClient
) {
    val values: List<ShopifyTaxonomyValue> = runBlocking {
        logger.info { "Downloading Shopify Taxonomy from Github" }

        val yaml = genericWebClient.get()
            .uri("https://raw.githubusercontent.com/Shopify/product-taxonomy/refs/heads/main/data/values.yml")
            .retrieve()
            .bodyToMono<String>()
            .awaitSingle()
        ObjectMapper(YAMLFactory()).registerKotlinModule().readValue(yaml)
    }
}

data class ShopifyTaxonomyValue(
    val id: Int,
    val name: String,
    @JsonProperty("friendly_id") val friendlyId: String,
    val handle: String
)
