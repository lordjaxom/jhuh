package de.hinundhergestellt.jhuh.tools

import arrow.atomic.AtomicInt
import de.hinundhergestellt.jhuh.HuhProperties
import de.hinundhergestellt.jhuh.core.mapIndexedParallel
import de.hinundhergestellt.jhuh.core.mapParallel
import de.hinundhergestellt.jhuh.vendors.shopify.client.LinkedMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.MetaobjectField
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetaobject
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetaobjectClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.findByLinkedMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.variantSkus
import de.hinundhergestellt.jhuh.vendors.shopify.taxonomy.ShopifyColorTaxonomy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.awt.Color
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

private val logger = KotlinLogging.logger { }

@Component
class ShopifyImageTools(
    private val productClient: ShopifyProductClient,
    private val variantClient: ShopifyProductVariantClient,
    private val optionClient: ShopifyProductOptionClient,
    private val mediaClient: ShopifyMediaClient,
    private val metaobjectClient: ShopifyMetaobjectClient,
    private val colorTaxonomy: ShopifyColorTaxonomy,
    private val syncImageTools: SyncImageTools,
    private val genericWebClient: WebClient,
    private val properties: HuhProperties
) {
    fun findAllImages(product: ShopifyProduct) =
        syncImageTools.findAllImages(product.syncImageProductName, product.variantSkus)

    suspend fun uploadProductImages(product: ShopifyProduct) {
        val imagesToUpload = syncImageTools.findProductImages(product.syncImageProductName)
        val uploadedMedias = mediaClient.upload(imagesToUpload.map { it.path })
        uploadedMedias.forEach { media -> media.altText = generateAltText(product) }
        mediaClient.update(uploadedMedias, referencesToAdd = listOf(product.id))
        // TODO: Add medias to product
    }

    suspend fun uploadVariantImages(product: ShopifyProduct, variants: Collection<ShopifyProductVariant>) {
        val imagesToUpload = syncImageTools.findVariantImages(product.syncImageProductName, variants.map { it.sku })
        if (imagesToUpload.isEmpty()) return

        val uploadedMedias = mediaClient.upload(imagesToUpload.map { it.path })
        variants.forEach { variant ->
            val media = uploadedMedias.first { it.fileName.isValidSyncImageFor(product, variant) }
            variant.mediaId = media.id
            media.altText = generateAltText(product, variant)
        }
        mediaClient.update(uploadedMedias, referencesToAdd = listOf(product.id))
        // TODO: Add medias to product
    }

    suspend fun generateColorSwatches(
        product: ShopifyProduct,
        variants: Collection<ShopifyProductVariant>,
        colorRect: RectPct = RectPct.CENTER_20,
        swatchRect: RectPct? = null,
        ignoreWhite: Boolean = false
    ) {
        require(swatchRect == null) { "swatchRect not yet supported" }

        val option = product.options.findByLinkedMetafield("shopify", "color-pattern") ?: return

        val swatchHandlePrefix = metaobjectClient.fetchDefinitionByType("shopify--color-pattern")!!
            .metaobjects.asSequence()
            .filter { metaobject -> option.optionValues.any { it.linkedMetafieldValue == metaobject.id } }
            .map { it.handle }
            .zipWithNext()
            .map { (current, next) -> current.commonPrefixWith(next) }
            .minByOrNull { it.length }
            ?.takeIf { it.isNotEmpty() }
            ?: "${generateColorSwatchHandle(product.syncImageProductName)}-"

        val imagesWithColor = syncImageTools.findVariantImages(product.syncImageProductName, variants.map { it.sku })
            .mapParallel(properties.processingThreads) { it to MediaImageTools.averageColorPercent(it.path, colorRect, ignoreWhite) }

        imagesWithColor.forEach { (image, color) ->
            val variant = variants.first { it.sku == image.variantSku }
            val optionValue = variant.options.first { it.name == option.name }
            val taxonomy = colorTaxonomy.findByColor(color)
            val metaobject = ShopifyMetaobject(
                "shopify--color-pattern",
                "$swatchHandlePrefix${generateColorSwatchHandle(optionValue.value)}",
                listOf(
                    MetaobjectField("label", optionValue.value),
                    MetaobjectField("color", color.toHex()),
                    MetaobjectField("color_taxonomy_reference", "[\"${taxonomy}\"]"),
                    MetaobjectField("pattern_taxonomy_reference", "gid://shopify/TaxonomyValue/2874")
                )
            )
            metaobjectClient.create(metaobject)
            optionValue.linkedMetafieldValue = metaobject.id
        }
    }

    suspend fun reorganizeProductImages(product: ShopifyProduct) { // TODO: Move to SyncImageTools
        require(product.variants.all { it.sku.isNotEmpty() }) { "All product variants must have SKU" }

        // save old media ids before attaching the new ones
        val mediaToDetach = product.media.toList()

        val images = normalizeMediaImages(product)
        TODO("Result of mediaClient.upload has different sort order than images!!!")
        val media = mediaClient.upload(images.map { it.imagePath })
            .asSequence()
            .zip(images.asSequence())
            .map { (media, image) -> media.apply { altText = image.altText; image.variant?.mediaId = id } }
            .toList()

        mediaClient.update(media, referencesToAdd = listOf(product.id))
        variantClient.update(product, images.mapNotNull { it.variant })
        mediaClient.update(mediaToDetach, referencesToRemove = listOf(product.id))
    }

    suspend fun replaceProductImages(product: ShopifyProduct) {
        val productName = product.syncImageProductName
        val images = syncImageTools.findProductImages(productName).map { it.path }
        if (images.isEmpty()) {
            logger.warn { "No product images found for $productName" }
            return
        }

        require(product.hasOnlyDefaultVariant) { "Currently only products without variants" }

        if (product.media.isNotEmpty()) {
            mediaClient.delete(product.media)
        }

        val media = mediaClient.upload(images).onEach { it.altText = generateAltText(product, null as ShopifyProductVariant?) }
        mediaClient.update(media, referencesToAdd = listOf(product.id))
    }

    suspend fun replaceProductVariantImages(product: ShopifyProduct) {
        val variantSkus = product.variants.map { it.sku }
        require(variantSkus.all { it.isNotEmpty() }) { "All product variants must have SKU" }

        // check if all images are accounted for before deleting
        val images = syncImageTools.findVariantImages(product.title.syncImageProductName, variantSkus)
        require(images.size == product.variants.size) { "Not all variants have an image" }

        mediaClient.delete(product.media.filter { media -> product.variants.any { it.mediaId == media.id } }.toList())

        val mediaVariants = mediaClient.upload(images.map { it.path })
            .asSequence()
            .zip(images.asSequence())
            .map { (media, image) -> media to product.variants.first { it.sku == image.variantSku } }
            .onEach { (media, variant) -> media.altText = generateAltText(product, variant); variant.mediaId = media.id }
            .toList()

        mediaClient.update(mediaVariants.map { it.first }, referencesToAdd = listOf(product.id))
        variantClient.update(product, mediaVariants.map { it.second })
    }

    suspend fun generateVariantColorSwatches(
        product: ShopifyProduct,
        swatchHandle: String,
        colorRect: RectPct = RectPct.EVERYTHING,
        swatchRect: RectPct? = null,
        ignoreWhite: Boolean = false
    ) {
        val productOption = product.options[0]

        require(product.variants.all { it.sku.isNotEmpty() }) { "All product variants must have SKU" }

        val images = evaluateMediaImages(product, colorRect, swatchRect, ignoreWhite)
        val media = swatchRect?.let { mediaClient.upload(images.mapNotNull { it.swatchFilePath }) }
        images.forEachIndexed { index, image ->
            val variantOption = image.variant.options[0]
            val metaobject = ShopifyMetaobject(
                "shopify--color-pattern",
                "$swatchHandle-${generateColorSwatchHandle(variantOption.value)}",
                listOfNotNull(
                    MetaobjectField("label", variantOption.value),
                    MetaobjectField("color", image.swatchColor!!.toHex()),
                    MetaobjectField("color_taxonomy_reference", "[\"${image.taxonomyReference}\"]"),
                    MetaobjectField("pattern_taxonomy_reference", "gid://shopify/TaxonomyValue/2874"),
                    media?.let { MetaobjectField("image", it[index].id) }
                )
            )
            metaobjectClient.create(metaobject)

            val index = productOption.optionValues.indexOfFirst { it.name == variantOption.value }
            TODO("Functionality broken")
            //productOption.optionValues[index] = productOption.optionValues[index].update { withLinkedMetafieldValue(metaobject.id) }
        }

        productOption.linkedMetafield = LinkedMetafield("shopify", "color-pattern")
        optionClient.update(product, productOption)
    }

    private suspend fun normalizeMediaImages(product: ShopifyProduct) = coroutineScope {
        val productName = product.syncImageProductName
        val indexOfNonVariantImage = AtomicInt(1)
        product.media.mapIndexedParallel(properties.processingThreads) { index, media ->
            logger.info { "Normalizing image ${index + 1} of ${product.media.size}" }
            normalizeMediaImage(productName, product, media, indexOfNonVariantImage)
        }
    }

    private suspend fun normalizeMediaImage(
        productName: String,
        product: ShopifyProduct,
        media: ShopifyMedia,
        indexOfNonVariantImage: AtomicInt
    ): NormalizedImage {
        val variant = product.variants.firstOrNull { it.mediaId == media.id }

        val imageFileExtension = extractImageFileExtension(media.src)
        val imageFileSuffix = variant?.sku?.syncImageSuffix ?: "produktbild-${indexOfNonVariantImage.andIncrement}"
        val imageFileName = generateImageFileName(productName, imageFileSuffix, imageFileExtension)
        val imageFilePath = properties.imageDirectory.resolve(productName).resolve(imageFileName)

        imageFilePath.parent.createDirectories()
        genericWebClient.downloadFileTo(media.src, imageFilePath)

        val finalImagePath =
            if (imageFileExtension != "webp") imageFilePath
            else {
                val newImageFileName = generateImageFileName(productName, imageFileSuffix, "png")
                val newImagePath = properties.imageDirectory.resolve(productName).resolve(newImageFileName)
                val result = Runtime.getRuntime().exec(arrayOf("convert", imageFilePath.toString(), newImagePath.toString())).waitFor()
                require(result == 0) { "Conversion of webp to png failed" }
                imageFilePath.deleteExisting()
                newImagePath
            }

        val altText = generateAltText(product, variant)
        return NormalizedImage(finalImagePath, variant, altText)
    }

    private suspend fun evaluateMediaImages(
        product: ShopifyProduct,
        colorRect: RectPct?,
        swatchRect: RectPct?,
        ignoreWhite: Boolean
    ) = coroutineScope {
        val productName = product.syncImageProductName
        val imagePath = properties.imageDirectory.resolve(productName)
        require(imagePath.isDirectory()) { "Image directory for $productName does not exist or is not a directory" }

        product.variants.mapIndexedParallel(properties.processingThreads) { index, variant ->
            logger.info { "Evaluating image ${index + 1} of ${product.variants.size}" }
            evaluateMediaImage(productName, variant, imagePath, colorRect, swatchRect, ignoreWhite)
        }
    }

    private fun evaluateMediaImage(
        productName: String,
        variant: ShopifyProductVariant,
        imagePath: Path,
        colorRect: RectPct?,
        swatchRect: RectPct?,
        ignoreWhite: Boolean
    ): EvaluatedImage {
        val imageFileSuffix = variant.sku.syncImageSuffix
        val imageFilePattern = generateImageFileName(productName, imageFileSuffix, "*")
        val imageFilePath = imagePath.listDirectoryEntries(imageFilePattern).first()

        val swatchColor = colorRect?.let { MediaImageTools.averageColorPercent(imageFilePath, it, ignoreWhite = ignoreWhite) }
        val taxonomyReference = swatchColor?.let { colorTaxonomy.findByColor(it) }

        val swatchFilePath = swatchRect
            ?.let { imageFilePath.parent.resolve("${imageFilePath.nameWithoutExtension}-swatch.${imageFilePath.extension}") }
            ?.also { MediaImageTools.extractColorSwatch(imageFilePath, swatchRect, it) }
        return EvaluatedImage(imageFilePath, variant, swatchColor, taxonomyReference, swatchFilePath)
    }

    private fun extractImageFileExtension(imageUrl: String) = URI(imageUrl).extension
    private fun generateColorSwatchHandle(optionValue: String) =
        UMLAUT_REPLACEMENTS
            .fold(optionValue) { value, (regex, replacement) -> value.replace(regex, replacement) }
            .replace(" ", "-")
            .lowercase()

    private fun generateAltText(product: ShopifyProduct, variant: ShopifyProductVariant? = null) =
        "${product.title} ${variant?.let { "in ${product.options[0].name} ${it.options[0].value}" } ?: "Produktbild"}"

    private class NormalizedImage(
        val imagePath: Path,
        val variant: ShopifyProductVariant?,
        val altText: String
    )

    private class EvaluatedImage(
        val imagePath: Path,
        val variant: ShopifyProductVariant,
        val swatchColor: Color?,
        val taxonomyReference: String?,
        val swatchFilePath: Path?
    )
}

val ShopifyProduct.syncImageProductName get() = title.syncImageProductName

fun String.isValidSyncImageFor(product: ShopifyProduct) =
    isValidSyncImageFor(product.syncImageProductName, product.variantSkus)

fun String.isValidSyncImageFor(product: ShopifyProduct, variant: ShopifyProductVariant) =
    isValidSyncImageFor(product.syncImageProductName, variant.sku)

private val UMLAUT_REPLACEMENTS = listOf(
    """[Ää]+""".toRegex() to "ae",
    """[Öö]+""".toRegex() to "oe",
    """[Üü]+""".toRegex() to "ue",
    """ß+""".toRegex() to "ss"
)