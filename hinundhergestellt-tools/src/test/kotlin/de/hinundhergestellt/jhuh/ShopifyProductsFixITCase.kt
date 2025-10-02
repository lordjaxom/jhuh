package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.hinundhergestellt.jhuh.tools.ShopifyImageTools
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldType
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetaobjectClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.findById
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import kotlin.io.path.writeText
import kotlin.test.Test

@SpringBootTest
@Disabled("Only run manually")
class ShopifyProductsFixITCase {

    @Autowired
    private lateinit var productClient: ShopifyProductClient

    @Autowired
    private lateinit var optionClient: ShopifyProductOptionClient

    @Autowired
    private lateinit var variantClient: ShopifyProductVariantClient

    @Autowired
    private lateinit var metafieldClient: ShopifyMetafieldClient

    @Autowired
    private lateinit var mediaClient: ShopifyMediaClient

    @Autowired
    private lateinit var metaobjectClient: ShopifyMetaobjectClient

    @Autowired
    private lateinit var shopifyImageTools: ShopifyImageTools

    @Test
    fun findAllProducts(): Unit = runBlocking {
        productClient.fetchAll().toList()
    }

    @Test
    fun findSingleProduct() = runBlocking {
        val product = productClient.fetchAll("'POLI-FLEX® PEARL GLITTER*'").toList()
        println(product)
    }

    @Test
    fun findMetaobjectDefinition() = runBlocking {
        val definition = metaobjectClient.fetchDefinitionByType("shopify--color-pattern")
        println(definition)
    }

    @Test
    fun updateAllProducts() = runBlocking {
        val products = productClient.fetchAll().toList()
        products.forEach {
            val old = it.descriptionHtml
            it.descriptionHtml = old.trim()
            if (old != it.descriptionHtml) {
                productClient.update(it)
            }
        }
    }

    @Test
    fun showCategories() = runBlocking {
        val products = productClient.fetchAll().toList()
        products.forEach {
            println("${it.title} -> ${it.category}")
        }
    }

    @Test
    fun updateGoogleAttributes() = runBlocking {
        val metaGoogleConditionNew = ShopifyMetafield("mm-google-shopping", "condition", "new", ShopifyMetafieldType.STRING)
        val metaGoogleCategoryToyCraftKits = ShopifyMetafield("mm-google-shopping", "google_product_category", "4986", ShopifyMetafieldType.STRING)
        val metaGoogleCategoryArtCraftKits = ShopifyMetafield("mm-google-shopping", "google_product_category", "505370", ShopifyMetafieldType.STRING)

        val products = productClient.fetchAll().toList()
        products.forEach { product ->
            var dirty = false

            val googleCondition = product.metafields.findById(metaGoogleConditionNew)
            if (googleCondition == null) {
                product.metafields.add(metaGoogleConditionNew)
                dirty = true
            } else if (googleCondition.value != metaGoogleConditionNew.value) {
                googleCondition.value = metaGoogleConditionNew.value
                dirty = true
            }

            val googleCategory = product.metafields.findById(metaGoogleCategoryToyCraftKits)
            if (googleCategory != null && googleCategory.value == metaGoogleCategoryToyCraftKits.value) {
                googleCategory.value = metaGoogleCategoryArtCraftKits.value
                dirty = true
            }

            if (dirty) {
                productClient.update(product)
            }
        }
    }

    @Test
    fun findProductsWithoutWeight() = runBlocking {
        val products = productClient.fetchAll().toList()
        products.forEach { product ->
            product.variants.forEach { variant ->
                if (variant.weight.compareTo(BigDecimal.ZERO) == 0) {
                    println("${product.title} - ${variant.title}")
                }
            }
        }
    }

    @Test
    fun findMediaWithoutAltText() = runBlocking {
        val products = productClient.fetchAll()
            .filter { product -> product.media.any { it.altText.isEmpty() } }
            .toList()
        if (products.isNotEmpty()) {
            val objectMapper = jacksonObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)
            homeDirectory
                .resolve("Dokumente/products.json")
                .writeText(objectMapper.writeValueAsString(products), StandardCharsets.UTF_8)
        } else println("No media without alt texts found")
    }

    @Test
    fun checkSomeProduct() = runBlocking {
        val product = productClient.fetchAll("'Silikon Gießform Mini-Häuschen I'").first()
        println(product)
    }
}