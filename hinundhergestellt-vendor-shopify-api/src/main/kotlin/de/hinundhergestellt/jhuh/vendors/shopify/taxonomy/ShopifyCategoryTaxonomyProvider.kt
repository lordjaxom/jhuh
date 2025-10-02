package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.hinundhergestellt.jhuh.core.loadTextResource

object ShopifyCategoryTaxonomyProvider {

    val categories: Map<String, ShopifyCategory> =
        ObjectMapper(YAMLFactory())
            .registerKotlinModule()
            .readValue<Map<String, Map<String, Map<String, RawCategory>>>>(loadTextResource { "/shopify-categories.yml" })
            .values.first()["categories"]!!.asSequence()
            .map { (key, value) -> "gid://shopify/TaxonomyCategory/$key" to value }
            .associate { (id, value) -> id to ShopifyCategory(id, value.name, value.context) }
}

private data class RawCategory(
    val name: String,
    val context: String
)

data class ShopifyCategory(
    val id: String,
    val name: String,
    val context: String
)