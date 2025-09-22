package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.hinundhergestellt.jhuh.tools.RectPct
import de.hinundhergestellt.jhuh.tools.ShopifyImageTools
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldType
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetaobjectClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyWeight
import de.hinundhergestellt.jhuh.vendors.shopify.client.findById
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.readText
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
    fun reorganizeProductImages() = runBlocking {
        val product = productClient.fetchAll("'Ricorumi Chenillove Sparkle, 100% PES, 100g'").first()
        shopifyImageTools.reorganizeProductImages(product)
    }

    @Test
    fun replaceProductImagesForAllWithoutMedia() = runBlocking {
        productClient.fetchAll()
            .filter { it.media.isEmpty() }
            .collect { shopifyImageTools.replaceProductImages(it) }
    }

    @Test
    fun replaceProductVariantImages() = runBlocking {
        val product = productClient.fetchAll("'Oracal® 631 Exhibition Cal Matt, 20cm x 30cm'").first()
        shopifyImageTools.replaceProductVariantImages(product)
    }

    @Test
    fun generateVariantColorSwatches() = runBlocking {
        val product = productClient.fetchAll("'Ricorumi Chenillove Sparkle, 100% PES, 100g'").first()
        shopifyImageTools.generateVariantColorSwatches(
            product,
            "aslan-ca23",
            RectPct(40.0, 10.0, 60.0, 30.0),
            null
        )
    }

    @Test
    fun findAndRenameImagesBySKU() = runBlocking {
        val imagePath = Path("/home/lordjaxom/Projects/Java/jhuh/hinundhergestellt-tools/src/test/python")
        val product = productClient.fetchAll().first { it.title.startsWith("craftcut® glänzend") }
        product.variants.forEach { variant ->
            val image = imagePath.listDirectoryEntries("*-${variant.sku}.jpg").firstOrNull()
            val newFileName = "craftcut-vinyl-glaenzend-${variant.sku.lowercase()}-${variant.title.replace(" ", "-").lowercase()}.jpg"
            image?.moveTo(imagePath.resolve(newFileName))
        }
    }

    @Test
    fun generateAltTextsAndAssociateMediaBySKU() = runBlocking {
        val product = productClient.fetchAll().first { it.title.startsWith("POLI-FLEX® PEARL GLITTER") }

        val variants = mutableListOf<ShopifyProductVariant>()
        val media = mutableListOf<ShopifyMedia>()
        product.variants.forEach { variant ->
            val image = product.media.find { it.src.contains("-${variant.sku.lowercase()}-") }
            if (image == null) return@forEach

            if (variant.mediaId == null) {
                variant.mediaId = image.id
                variants.add(variant)
            }
            if (image.altText.isEmpty()) {
                image.altText = "${product.title} in Farbe ${variant.options[0].value}"
                media.add(image)
            }
        }

        variantClient.update(product, variants)
        mediaClient.update(media)
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
                if (variant.weight.value.compareTo(BigDecimal.ZERO) == 0) {
                    println("${product.title} - ${variant.title}")
                }
            }
        }
    }

    @Test
    fun assignWeightToProducts() = runBlocking {
        productClient.fetchAll()
            .filter { it.title.startsWith("Ricorumi Nilli Nilli") }
            .collect { product ->
                product.variants.forEach { it.weight = ShopifyWeight(WeightUnit.GRAMS, BigDecimal("25.0")) }
                variantClient.update(product, product.variants)
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
    fun updateAltTexts() = runBlocking {
        val fileContent = homeDirectory
            .resolve("Downloads/alt_texts_from_filenames.json")
            .readText(StandardCharsets.UTF_8)
        jacksonObjectMapper()
            .readValue<List<MediaAltText>>(fileContent)
            .map { ShopifyMedia(it.id, "", it.altText) }
            .chunked(250)
            .forEach { mediaClient.update(it) }
    }

    @Test
    fun updateAltTextsFromRayherFilenames() = runBlocking {
        val products = productClient.fetchAll()
            .filter { product -> product.title.startsWith("Silikon Gießform") }
            .toList()
        products
            .flatMap { product ->
                product.media.mapNotNull { media ->
                    val type = when {
                        media.src.contains("_DI") -> " - Produktbeispiel"
                        media.src.contains("_VP") -> " - Verpackung"
                        media.src.contains("_PF") -> " - Produktfoto"
                        else -> ""
                    }
                    val altText = "${product.title}$type"
                    if (media.altText != altText) {
                        media.altText = altText
                        media
                    } else null
                }
            }
            .chunked(250)
            .forEach { mediaClient.update(it) }
    }

    @Test
    fun updateOptionValueWithLinkedMetafield() = runBlocking {
        val product = productClient.fetchAll("'Testprodukt'").first()
        val option = product.options[0]
        val index = option.optionValues.indexOfFirst { it.name == "Sky Blue" }
//        option.optionValues[index] = option.optionValues[index].update { withName("Sky blue") }
        optionClient.update(product, product.options[0])
    }
}

class MediaAltText(
    val id: String,
    val altText: String
)