package de.hinundhergestellt.jhuh.tools

import arrow.atomic.AtomicInt
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.awt.Color
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

private val logger = KotlinLogging.logger { }

@Component
class ShopifyTools(
    private val productClient: ShopifyProductClient,
    private val variantClient: ShopifyProductVariantClient,
    private val optionClient: ShopifyProductOptionClient,
    private val mediaClient: ShopifyMediaClient,
    private val metaobjectClient: ShopifyMetaobjectClient,
    private val toolsWebClient: WebClient,
    @Value("\${hinundhergestellt.image-directory}") private val imageDirectory: Path,
    @Value("\${hinundhergestellt.download-threads}") private val downloadThreads: Int
) {
    suspend fun reorganizeProductImages(product: ShopifyProduct) {
        require(product.variants.all { it.sku.isNotEmpty() }) { "All product variants must have SKU" }

        // save old media ids before attaching the new ones
        val mediaToDetach = product.media.toList()

        val images = normalizeMediaImages(product)
        val media = mediaClient.upload(images.map { it.imagePath })
            .asSequence()
            .zip(images.asSequence())
            .map { (media, image) -> media.apply { altText = image.altText; image.variant?.mediaId = id } }
            .toList()

        mediaClient.update(media, referencesToAdd = listOf(product.id))
        variantClient.update(product, images.mapNotNull { it.variant })
        mediaClient.update(mediaToDetach, referencesToRemove = listOf(product.id))
    }

    suspend fun generateVariantColorSwatches(product: ShopifyProduct, swatchHandle: String, rect: Rect = Rect.EVERYTHING) {
        val productOption = product.options[0]

        require(product.variants.all { it.sku.isNotEmpty() }) { "All product variants must have SKU" }

        val images = evaluateMediaImages(product, rect)
        images.forEach { image ->
            val variantOption = image.variant.options[0]
            val metaobject = metaobjectClient.create(
                UnsavedShopifyMetaobject(
                    "shopify--color-pattern",
                    "$swatchHandle-${generateColorSwatchHandle(variantOption.value)}",
                    listOf(
                        MetaobjectField("label", variantOption.value),
                        MetaobjectField("color", image.swatchColor.toHex()),
                        MetaobjectField("color_taxonomy_reference", "[\"${image.taxonomyReference}\"]"),
                        MetaobjectField("pattern_taxonomy_reference", "gid://shopify/TaxonomyValue/2874")
                    )
                )
            )

            val index = productOption.optionValues.indexOfFirst { it.name == variantOption.value }
            productOption.optionValues[index] = productOption.optionValues[index].update { withLinkedMetafieldValue(metaobject.id) }
        }

        productOption.linkedMetafield = LinkedMetafield("shopify", "color-pattern")
        optionClient.update(product, productOption)
    }

    private suspend fun normalizeMediaImages(product: ShopifyProduct) = coroutineScope {
        val productName = extractProductName(product)
        val indexOfNonVariantImage = AtomicInt(1)
        val semaphore = Semaphore(downloadThreads)
        product.media
            .mapIndexed { index, media ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        logger.info { "Normalizing image ${index + 1} of ${product.media.size}" }
                        normalizeMediaImage(productName, product, media, indexOfNonVariantImage)
                    }
                }
            }
            .awaitAll()
    }

    private suspend fun normalizeMediaImage(
        productName: String,
        product: ShopifyProduct,
        media: ShopifyMedia,
        indexOfNonVariantImage: AtomicInt
    ): NormalizedImage {
        val variant = product.variants.firstOrNull { it.mediaId == media.id }

        val imageFileExtension = extractImageFileExtension(media.src)
        val imageFileSuffix = variant?.sku?.let { generateImageFileSuffix(it) } ?: "produktbild-${indexOfNonVariantImage.andIncrement}"
        val imageFileName = generateImageFileName(productName, imageFileSuffix, imageFileExtension)
        val imagePath = imageDirectory.resolve(productName).resolve(imageFileName)

        imagePath.parent.createDirectories()
        toolsWebClient.downloadFileTo(media.src, imagePath)

        val finalImagePath =
            if (imageFileExtension != "webp") imagePath
            else {
                val newImageFileName = generateImageFileName(productName, imageFileSuffix, "png")
                val newImagePath = imageDirectory.resolve(productName).resolve(newImageFileName)
                val result = Runtime.getRuntime().exec(arrayOf("convert", imagePath.toString(), newImagePath.toString())).waitFor()
                require(result == 0) { "Conversion of webp to png failed" }
                imagePath.deleteExisting()
                newImagePath
            }

        val altText = "$productName ${variant?.let { "in Farbe ${it.options[0].value}" } ?: " Produktbild"}"
        return NormalizedImage(finalImagePath, variant, altText)
    }

    private suspend fun evaluateMediaImages(product: ShopifyProduct, rect: Rect) = coroutineScope {
        val productName = extractProductName(product)
        val imagePath = imageDirectory.resolve(productName)
        require(imagePath.isDirectory()) { "Image directory for $productName does not exist or is not a directory" }
        val semaphore = Semaphore(downloadThreads)
        product.variants
            .mapIndexed { index, variant ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        logger.info { "Evaluating image ${index + 1} of ${product.variants.size}" }
                        evaluateMediaImage(productName, variant, imagePath, rect)
                    }
                }
            }
            .awaitAll()
    }

    private fun evaluateMediaImage(productName: String, variant: ShopifyProductVariant, imagePath: Path, rect: Rect): EvaluatedImage {
        val imageFileSuffix = generateImageFileSuffix(variant.sku)
        val imageFilePattern = generateImageFileName(productName, imageFileSuffix, "*")
        val imageFilePath = imagePath.listDirectoryEntries(imageFilePattern).first()

        val swatchColor = MediaImageTools.averageColorPercent(imageFilePath, rect)
        val taxonomyReference = ShopifyColorTaxonomy.findByColor(swatchColor)

        return EvaluatedImage(variant, swatchColor, taxonomyReference)
    }

    private fun extractProductName(product: ShopifyProduct) = product.title.substringBefore(",")

    private fun generateImageFileName(productName: String, suffix: String, extension: String): String {
        val productNamePart = IMAGE_FILE_NAME_REPLACEMENTS
            .fold(productName) { value, (regex, replacement) -> value.replace(regex, replacement) }
            .lowercase()
        return "${productNamePart}-${suffix}.$extension"
    }

    private fun generateImageFileSuffix(sku: String) = sku.replace(" ", "-").lowercase()
    private fun extractImageFileExtension(imageUrl: String) = URI(imageUrl).path.substringAfterLast(".")

    private fun generateColorSwatchHandle(optionValue: String) =
        UMLAUT_REPLACEMENTS
            .fold(optionValue) { value, (regex, replacement) -> value.replace(regex, replacement) }
            .replace(" ", "-")
            .lowercase()

    private class NormalizedImage(
        val imagePath: Path,
        val variant: ShopifyProductVariant?,
        val altText: String
    )

    private class EvaluatedImage(
        val variant: ShopifyProductVariant,
        val swatchColor: Color,
        val taxonomyReference: String
    )
}

private val UMLAUT_REPLACEMENTS = listOf(
    """[Ää]+""".toRegex() to "ae",
    """[Öö]+""".toRegex() to "oe",
    """[Üü]+""".toRegex() to "ue",
    """ß+""".toRegex() to "ss"
)

private val IMAGE_FILE_NAME_REPLACEMENTS = listOf(
    *UMLAUT_REPLACEMENTS.toTypedArray(),
    """[^A-Za-z0-9 -]+""".toRegex() to "",
    """^\s+""".toRegex() to "",
    """\s+$""".toRegex() to "",
    """\s+""".toRegex() to "-"
)
