package de.hinundhergestellt.jhuh.tools

import arrow.atomic.AtomicInt
import de.hinundhergestellt.jhuh.HuhProperties
import de.hinundhergestellt.jhuh.core.forEachParallel
import de.hinundhergestellt.jhuh.core.mapIndexedParallel
import de.hinundhergestellt.jhuh.core.mapParallel
import de.hinundhergestellt.jhuh.vendors.shopify.client.MetaobjectField
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetaobject
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetaobjectClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.findByLinkedMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.variantSkus
import de.hinundhergestellt.jhuh.vendors.shopify.taxonomy.ShopifyColorTaxonomy
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting

private val logger = KotlinLogging.logger { }

@Component
class ShopifyImageTools(
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
        syncImageTools.findAllImages(product.vendor, product.syncImageProductName, product.variantSkus)

    suspend fun uploadProductImages(product: ShopifyProduct, images: List<SyncImage>? = null) {
        val imagesToUpload = images ?: syncImageTools.findProductImages(product.vendor, product.syncImageProductName)
        if (imagesToUpload.isEmpty()) return

        require(imagesToUpload.all { it.variantSku == null }) { "Variant images not supported yet" }

        val uploadedMedias = mediaClient.upload(imagesToUpload.map { it.path })
        uploadedMedias.forEach { media -> media.altText = generateAltText(product) }
        mediaClient.update(uploadedMedias, referencesToAdd = listOf(product.id))
        product.media += uploadedMedias
    }

    suspend fun uploadVariantImages(product: ShopifyProduct, variants: Collection<ShopifyProductVariant>) {
        val imagesToUpload = syncImageTools.findVariantImages(product.vendor, product.syncImageProductName, variants.map { it.sku })
        if (imagesToUpload.isEmpty()) return

        val uploadedMedias = mediaClient.upload(imagesToUpload.map { it.path })
        variants.forEach { variant ->
            val media = uploadedMedias.first { it.fileName.isValidSyncImageFor(product, variant) }
            variant.mediaId = media.id
            media.altText = generateAltText(product, variant)
        }
        mediaClient.update(uploadedMedias, referencesToAdd = listOf(product.id))
        product.media += uploadedMedias
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

        val imagesWithColor = syncImageTools.findVariantImages(product.vendor, product.syncImageProductName, variants.map { it.sku })
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

    fun unorganizedProductImages(product: ShopifyProduct) =
        product.media.filter { !it.fileName.isValidSyncImageFor(product) }

    suspend fun reorganizeProductImages(product: ShopifyProduct, mediaToReorganize: List<ShopifyMedia>) {
        require(product.hasOnlyDefaultVariant || product.variants.all { it.sku.isNotEmpty() }) { "All product variants must have SKU" }

        val images = normalizeProductImages(product, mediaToReorganize)
        val medias = mediaClient.upload(images.map { it.path })
        val variants = images.asSequence().zip(medias.asSequence())
            .mapNotNull { (image, media) ->
                val variant = image.variantSku?.let { product.findVariantBySku(it) }
                media.altText = generateAltText(product, variant)
                variant.also { it?.mediaId = media.id }
            }
            .toList()

        mediaClient.update(medias, referencesToAdd = listOf(product.id))
        if (variants.isNotEmpty()) variantClient.update(product, variants)
        mediaClient.update(mediaToReorganize, referencesToRemove = listOf(product.id))
    }

    fun locallyMissingProductImages(product: ShopifyProduct): List<ShopifyMedia> {
        val images = findAllImages(product)
        return product.media.filter { media ->
            media.fileName.isValidSyncImageFor(product) && !images.any { it.path.fileName.toString() == media.fileName }
        }
    }

    suspend fun downloadLocallyMissingProductImages(product: ShopifyProduct, mediaToDownload: List<ShopifyMedia>) {
        val directory = properties.imageDirectory.resolve(product.vendor).resolve(product.syncImageProductName)
        directory.createDirectories()
        mediaToDownload.forEachParallel(properties.processingThreads) { media ->
            logger.info { "Downloading image ${media.fileName}" }
            val path = directory.resolve(media.fileName)
            genericWebClient.downloadFileTo(media.src, path)
        }
    }

    fun remotelyMissingProductImages(product: ShopifyProduct): List<SyncImage> {
        val images = findAllImages(product)
        return images.filter { image -> product.media.none { it.fileName == image.path.fileName.toString() } }
    }

    private suspend fun normalizeProductImages(product: ShopifyProduct, medias: List<ShopifyMedia>): List<SyncImage> {
        val indexOfNonVariantImage = AtomicInt(1)
        return medias.mapIndexedParallel(properties.processingThreads) { index, media ->
            logger.info { "Normalizing image ${index + 1} of ${medias.size}" }
            normalizeProductImage(product, media, indexOfNonVariantImage)
        }
    }

    private suspend fun normalizeProductImage(product: ShopifyProduct, media: ShopifyMedia, indexOfNonVariantImage: AtomicInt): SyncImage {
        val variant = product.variants.firstOrNull { it.mediaId == media.id }
        val suffix = variant?.syncImageSuffix ?: findNextAvailableProductFileName(product, indexOfNonVariantImage)
        val fileName = generateImageFileName(product.syncImageProductName, suffix, URI(media.src).extension)
        var path = properties.imageDirectory.resolve(product.vendor).resolve(product.syncImageProductName).resolve(fileName)

        path.parent.createDirectories()
        genericWebClient.downloadFileTo(media.src, path)

        if (URI(media.src).extension == "webp") {
            val newFileName = generateImageFileName(product.syncImageProductName, suffix, "png")
            val newPath = properties.imageDirectory.resolve(product.syncImageProductName).resolve(newFileName)
            val result = Runtime.getRuntime().exec(arrayOf("convert", path.toString(), newPath.toString())).waitFor()
            require(result == 0) { "Conversion of webp to png failed" }
            path.deleteExisting()
            path = newPath
        }
        return SyncImage(path, variant?.sku)
    }

    private fun findNextAvailableProductFileName(product: ShopifyProduct, index: AtomicInt): String {
        while (true) {
            val suffix = "produktbild-${index.andIncrement}"
            val candidate = generateImageFileName(product.syncImageProductName, suffix, "")
            if (product.media.none { it.fileName.startsWith(candidate) }) return suffix
        }
    }

    private fun generateColorSwatchHandle(optionValue: String) =
        UMLAUT_REPLACEMENTS
            .fold(optionValue) { value, (regex, replacement) -> value.replace(regex, replacement) }
            .replace(" ", "-")
            .lowercase()

    private fun generateAltText(product: ShopifyProduct, variant: ShopifyProductVariant? = null) =
        "${product.title} ${variant?.let { "in ${product.options[0].name} ${it.options[0].value}" } ?: "Produktbild"}"
}

val ShopifyProduct.syncImageProductName get() = title.syncImageProductName
val ShopifyProductVariant.syncImageSuffix get() = sku.syncImageSuffix

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