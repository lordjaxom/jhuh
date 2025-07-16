package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldsClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyWeight
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.nio.charset.StandardCharsets
import kotlin.collections.filter
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.sequences.filter
import kotlin.sequences.toList
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
    private lateinit var metafieldsClient: ShopifyMetafieldsClient

    @Autowired
    private lateinit var mediaClient: ShopifyMediaClient

    @Test
    fun findAllProducts(): Unit = runBlocking {
        productClient.fetchAll().toList()
    }

    @Test
    fun associateMedia(): Unit = runBlocking {
        val product = productClient.fetchAll().first { it.title.startsWith("craftcut® glänzend") }
        val changedVariants = product.variants.asSequence()
            .filter { it.options.isNotEmpty() && it.mediaId == null }
            .map {
                it to product.media.filter { image ->
                    image.src.contains("""\b${Regex.fromLiteral(it.sku)}\b""".toRegex(RegexOption.IGNORE_CASE))
                }
            }
            .filter { (_, images) -> images.size == 1 }
            .onEach { (variant, images) -> variant.mediaId = images[0].id }
            .map { (variant, _) -> variant }
            .toList()
        variantClient.update(product, changedVariants)
    }

    @Test
    fun findProductsWithoutWeight() = runBlocking {
        val products = productClient.fetchAll().toList()
        products.forEach { product ->
            if (product.variants.any { it.weight.value == 0.0 }) {
                println(product.title)
            }
        }
    }

    @Test
    fun assignWeightToProducts() = runBlocking {
        productClient.fetchAll()
            .filter { it.title.contains("Siser® Aurora") }
            .collect { product ->
                product.variants.forEach { it.weight = ShopifyWeight(WeightUnit.GRAMS, 0.5) }
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
        }
        else println("No media without alt texts found")
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
}

class MediaAltText(
    val id: String,
    val altText: String
)