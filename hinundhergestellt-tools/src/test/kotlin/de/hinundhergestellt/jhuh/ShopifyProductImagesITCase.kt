package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.tools.MediaDownloadWebClient
import de.hinundhergestellt.jhuh.tools.MediaImageTools
import de.hinundhergestellt.jhuh.tools.ShopifyColorTaxonomy
import de.hinundhergestellt.jhuh.tools.toHex
import de.hinundhergestellt.jhuh.vendors.shopify.client.LinkedMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.MetaobjectField
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetaobjectClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyMetaobject
import de.hinundhergestellt.jhuh.vendors.shopify.client.update
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.awt.Color
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.Test

private val logger = KotlinLogging.logger { }

@SpringBootTest
@Disabled("Only run manually")
class ShopifyProductImagesITCase {

    @Autowired
    private lateinit var productClient: ShopifyProductClient

    @Autowired
    private lateinit var variantClient: ShopifyProductVariantClient

    @Autowired
    private lateinit var mediaClient: ShopifyMediaClient

    @Autowired
    private lateinit var metaobjectClient: ShopifyMetaobjectClient

    @Autowired
    private lateinit var optionClient: ShopifyProductOptionClient

    @Autowired
    private lateinit var mediaDownloadWebClient: MediaDownloadWebClient

    @Test
    fun reorganizeImagesOfExistingProductWithSwatches() = runBlocking {
        val productName = "Ricorumi Nilli Nilli Kopie"
        val productHandle = "ricorumi-nilli-kopie"

        val product = productClient.fetchAll("'$productName*'").first()

        // save variants' media before attaching the new ones
        val mediaToDetach = product.media.toList()

        val images = processMediaImages(product)

        mediaClient.upload(images.map { it.imagePath }).asSequence()
            .zip(images.asSequence())
            .forEach { (media, processedImage) ->
                media.altText = processedImage.altText
                processedImage.media = media

                if (processedImage.variant != null) {
                    processedImage.variant.mediaId = media.id

                    val metaobject = metaobjectClient.create(
                        UnsavedShopifyMetaobject(
                            "shopify--color-pattern",
                            "$productHandle-${processedImage.variant.options[0].value.replace(" ", "-").lowercase()}",
                            listOf(
                                MetaobjectField("label", processedImage.variant.options[0].value),
                                MetaobjectField("color", processedImage.swatchColor.toHex()),
                                MetaobjectField("color_taxonomy_reference", "[\"${processedImage.taxonomyReference}\"]"),
                                MetaobjectField("pattern_taxonomy_reference", "gid://shopify/TaxonomyValue/2874")
                            )
                        )
                    )

                    val option = product.options[0]
                    val index = option.optionValues.indexOfFirst { it.name == processedImage.variant.options[0].value }
                    option.optionValues[index] = option.optionValues[index].update { withLinkedMetafieldValue(metaobject.id) }
                }
            }

        product.options[0].linkedMetafield = LinkedMetafield("shopify", "color-pattern")
        optionClient.update(product, product.options[0])
        mediaClient.update(images.mapNotNull { it.media }, referencesToAdd = listOf(product.id))
        variantClient.update(product, images.mapNotNull { it.variant })
        mediaClient.update(mediaToDetach, referencesToRemove = listOf(product.id))
    }

    suspend fun processMediaImages(product: ShopifyProduct): List<ProcessedImage> {
        var indexOfNonVariantImage = 1
        return product.media.mapIndexed { index, media ->
            val variant = product.variants.firstOrNull { it.mediaId == media.id }
            require(variant == null || variant.sku.isNotEmpty()) { "Variant must have SKU" }

            val productName = product.title.substringBefore(",")
            val fileSuffix = variant?.sku?.replace(" ", "-")?.lowercase() ?: "produktbild-${indexOfNonVariantImage++}"
            val imageFileName = generateImageFileName(productName, fileSuffix, media.src)
            val imagePath = workDirectory.resolve("var/images").resolve(productName).resolve(imageFileName)
            if (!imagePath.exists()) {
                imagePath.parent.createDirectories()
                mediaDownloadWebClient.downloadFileTo(media.src, imagePath)

                logger.info { "Downloaded image $index of ${product.media.size}" }
            }

            val altText = "$productName ${variant?.let { "in Farbe ${it.options[0].value}" } ?: " - Produktbild"}"
            val swatchColor = MediaImageTools.averageColorPercent(imagePath)
            val taxonomyReference = ShopifyColorTaxonomy.findByColor(swatchColor)

            ProcessedImage(imagePath, variant, altText, swatchColor, taxonomyReference)
        }
    }
}

class ProcessedImage(
    val imagePath: Path,
    val variant: ShopifyProductVariant?,
    val altText: String,
    val swatchColor: Color,
    val taxonomyReference: String,
    var media: ShopifyMedia? = null
)

private fun generateImageFileName(productName: String, suffix: String, imageUrl: String): String {
    val fileName = URI(imageUrl).path.substringAfterLast("/")
    val extension = fileName.substringAfterLast(".")
    val productNamePart = IMAGE_FILE_NAME_REPLACEMENTS
        .fold(productName) { value, (regex, replacement) -> value.replace(regex, replacement) }
        .lowercase()
    return "${productNamePart}-${suffix}.$extension"
}
