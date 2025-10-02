package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.hinundhergestellt.jhuh.core.loadTextResource

object ShopifyTaxonomyProvider {

    val taxonomy: List<ShopifyTaxonomyValue> =
        ObjectMapper(YAMLFactory())
            .registerKotlinModule()
            .readValue(loadTextResource { "/shopify-taxonomy.yml" })
}

data class ShopifyTaxonomyValue(
    val id: Int,
    val name: String,
    @field:JsonProperty("friendly_id")
    val friendlyId: String,
    val handle: String
)