package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.hinundhergestellt.jhuh.tools.MediaDownloadWebClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.LinkedMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.MetaobjectField
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetaobjectClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyWeight
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyMetaobject
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.sqrt
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
    private lateinit var metafieldsClient: ShopifyMetafieldClient

    @Autowired
    private lateinit var mediaClient: ShopifyMediaClient

    @Autowired
    private lateinit var metaobjectClient: ShopifyMetaobjectClient

    @Autowired
    private lateinit var mediaDownloadWebClient: MediaDownloadWebClient

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
    fun replaceImagesAndAssociate() = runBlocking {
        val imagePath = Path("/home/lordjaxom/Dokumente/Hin-undHergestellt/Shopify TEMP/POLI-TAPE TUBITHERM")
        val product = productClient.fetchAll("'POLI-TAPE® TUBITHERM®*'").first()

        // save variants' media before attaching the new ones
        val mediaToDetach = product.media.filter { media -> product.variants.any { it.mediaId == media.id } }

        val imagesWithVariant = imagePath
            .listDirectoryEntries("poli-tape-tubitherm-*.png")
            .asSequence()
            .map { image -> image to product.variants.first { image.fileName.toString().contains("-${it.sku.substring(4, 7)}-") } }
            .toList()

        val mediasWithVariant = mediaClient
            .upload(imagesWithVariant.map { it.first })
            .asSequence()
            .zip(imagesWithVariant.asSequence().map { it.second })
            .onEach { (media, variant) ->
                media.altText = "${product.title} in Farbe ${variant.options[0].value}"
                variant.mediaId = media.id
            }
            .toList()

        mediaClient.update(mediasWithVariant.map { it.first }, referencesToAdd = listOf(product.id))
        variantClient.update(product, mediasWithVariant.map { it.second })
        mediaClient.update(mediaToDetach, referencesToRemove = listOf(product.id))
    }

    @Test
    fun createColorSwatchesAndAssociate() = runBlocking {
        val definition = metaobjectClient.fetchDefinitionByType("shopify--color-pattern")!!
        val taxonomyReferences = definition.metaobjects.asSequence()
            .map { metaobject -> metaobject.fields.find { it.key == "color" }!!.value!! to metaobject.fields.find { it.key == "color_taxonomy_reference" }!!.value!! }
            .toList()

        fun hexToRgb(hex: String): Triple<Int, Int, Int> {
            val cleanHex = hex.removePrefix("#")
            val r = cleanHex.substring(0, 2).toInt(16)
            val g = cleanHex.substring(2, 4).toInt(16)
            val b = cleanHex.substring(4, 6).toInt(16)
            return Triple(r, g, b)
        }

        fun colorDistance(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Double {
            val dr = a.first - b.first
            val dg = a.second - b.second
            val db = a.third - b.third
            return sqrt((dr * dr + dg * dg + db * db).toDouble())
        }

        fun findClosestTaxonomy(inputHex: String): String {
            val targetRgb = hexToRgb(inputHex)
            return taxonomyReferences.minByOrNull { colorDistance(hexToRgb(it.first), targetRgb) }!!.second
        }

        val imagePath = Path("/home/lordjaxom/Dokumente/Hin-undHergestellt/Shopify TEMP/POLI-TAPE TUBITHERM")
        val product = productClient.fetchAll("'POLI-TAPE® TUBITHERM®*'").first()

//        val optionValuesToUpdate = mutableListOf<ProductOptionValue>()
        imagePath
            .listDirectoryEntries("poli-tape-tubitherm-*.png")
            .map { image -> image to product.variants.first { image.fileName.toString().contains("-${it.sku.substring(4, 7)}-") } }
            .map { (image, variant) ->
                Triple(
                    image,
                    variant,
                    product.options[0].optionValues.first { it.name == variant.options[0].value })
            }
            .forEach { (image, variant, optionValue) ->
                val process = Runtime.getRuntime().exec(
                    arrayOf(
                        "python",
                        "/home/lordjaxom/Projects/Java/jhuh/hinundhergestellt-tools/src/test/python/dominant_color.py",
                        image.toString(),
                        "100"
                    )
                )
                val color = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().trim() }

                val handle = "poli-tubitherm-${variant.options[0].value.lowercase().replace(" ", "-")}"
                val taxonomyReference = findClosestTaxonomy(color)
                val unsaved = UnsavedShopifyMetaobject(
                    "shopify--color-pattern",
                    handle,
                    listOf(
                        MetaobjectField("label", variant.options[0].value),
                        MetaobjectField("color", color),
                        MetaobjectField("color_taxonomy_reference", taxonomyReference),
                        MetaobjectField("pattern_taxonomy_reference", "gid://shopify/TaxonomyValue/2874")
                    )
                )
                val metaobject = metaobjectClient.create(unsaved)

//                optionValuesToUpdate.add(optionValue.update { withLinkedMetafieldValue(metaobject.id) })
            }

        // NOTIZEN warum das hier vmtl. funktioniert hat ohne optionValuesToUpdate jemals abzuschicken:
        // Shopify erlaubt nicht, in einer ProductOption sowohl name als auch linkedMetafieldValue gesetzt zu haben
        // Wenn aber in Option `linkedMetafield` gesetzt ist und in OptionValue `name` aber nicht `linkedMetafieldValue`, so scheint Shopify
        // selbständig eine passende MetafieldValue zum Namen rauszusuchen.

        product.options[0].linkedMetafield = LinkedMetafield("shopify", "color-pattern")
        optionClient.update(product, product.options[0])
    }

    @Test
    fun checkColorSwatches() = runBlocking {
        val definition = metaobjectClient.fetchDefinitionByType("shopify--color-pattern")!!
        val product = productClient.fetchAll("'craftcut® glänzend*'").first()
        product.options[0].optionValues.forEach { optionValue ->
            val metafield = definition.metaobjects.first { it.id == optionValue.linkedMetafieldValue }
            println("${optionValue.name} -> ${metafield.handle}")
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
    fun updateColorMetaobjects() = runBlocking {
        val definition = metaobjectClient.fetchDefinitionByType("shopify--color-pattern")!!
        definition.metaobjects
            .filter { !it.handle.startsWith("pearl-glitter") }
            .onEach { it.handle = "craftcut-glossy-${it.handle}" }
            .forEach { metaobjectClient.update(it) }
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