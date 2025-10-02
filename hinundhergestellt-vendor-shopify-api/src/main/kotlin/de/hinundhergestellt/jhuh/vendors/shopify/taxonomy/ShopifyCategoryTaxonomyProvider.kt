package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import de.hinundhergestellt.jhuh.core.loadTextResource

object ShopifyCategoryTaxonomyProvider {

    val categories = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .readValue<Map<String, Map<String, Map<String, RawCategory>>>>(loadTextResource { "/shopify-categories.yml" })
        .values.first()["categories"]!!.asSequence()
        .map { (key, value) -> value.toShopifyCategory(key) }
        .associateBy { it.id }
}

private data class RawCategory(
    val name: String,
    val context: String
) {
    fun toShopifyCategory(key: String) = ShopifyCategory("gid://shopify/TaxonomyCategory/$key", name, context)
}

data class ShopifyCategory(
    val id: String,
    val name: String,
    val context: String
)