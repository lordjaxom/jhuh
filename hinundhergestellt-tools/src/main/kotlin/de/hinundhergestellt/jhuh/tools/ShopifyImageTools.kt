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
import de.hinundhergestellt.jhuh.vendors.shopify.taxonomy.ShopifyColorTaxonomyProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.text.Regex.Companion.escape

private val logger = KotlinLogging.logger { }

@Component
class ShopifyImageTools(
    private val variantClient: ShopifyProductVariantClient,
    private val optionClient: ShopifyProductOptionClient,
    private val mediaClient: ShopifyMediaClient,
    private val metaobjectClient: ShopifyMetaobjectClient,
    private val syncImageTools: SyncImageTools,
    private val genericWebClient: WebClient,
    private val properties: HuhProperties
) {
    fun findAllImages(product: ShopifyProduct) =
        syncImageTools.findAllImages(product.vendor, product.productNameForImages, product.variantSkus)

    suspend fun uploadProductImages(product: ShopifyProduct, imagesToUpload: List<SyncImage>) {
        require(imagesToUpload.all { it.variantSku == null }) { "Variant images not supported, use uploadVariantImages instead" }

        val imagesWithShopifyName =
            imagesToUpload.associate { it.path to "${toProductImageNameWithoutExtension(product, it.index)}.${it.path.extension}" }
        val uploadedMedias = mediaClient.upload(imagesWithShopifyName)
        uploadedMedias.forEach { media -> media.altText = generateAltText(product) }
        mediaClient.update(uploadedMedias, referencesToAdd = listOf(product.id))
        product.media += uploadedMedias
    }

    suspend fun uploadProductImages(product: ShopifyProduct) {
        val imagesToUpload = syncImageTools.findProductImages(product.vendor, product.productNameForImages)
        if (imagesToUpload.isEmpty()) return

        uploadProductImages(product, imagesToUpload)
    }

    suspend fun uploadVariantImages(product: ShopifyProduct, variants: Collection<ShopifyProductVariant>) {
        val imagesToUpload = syncImageTools.findVariantImages(product.vendor, product.productNameForImages, variants.map { it.sku })
        if (imagesToUpload.isEmpty()) return

        val imagesWithShopifyName =
            imagesToUpload.associate { it.path to "${toVariantImageNameWithoutExtension(product, it.variantSku!!)}.${it.path.extension}" }
        val uploadedMedias = mediaClient.upload(imagesWithShopifyName)
        variants.forEach { variant ->
            val media = uploadedMedias.first { it.fileName.isVariantShopifyImage(product, variant) }
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
            ?: "${generateColorSwatchHandle(product.productNameForImages)}-"

        val imagesWithColor = syncImageTools.findVariantImages(product.vendor, product.productNameForImages, variants.map { it.sku })
            .mapParallel(properties.processingThreads) { it to MediaImageTools.averageColorPercent(it.path, colorRect, ignoreWhite) }

        imagesWithColor.forEach { (image, color) ->
            val variant = variants.first { it.sku == image.variantSku }
            val optionValue = variant.options.first { it.name == option.name }
            val taxonomy = ShopifyColorTaxonomyProvider.findNearestByColor(color)
            val metaobject = ShopifyMetaobject(
                "shopify--color-pattern",
                "$swatchHandlePrefix${generateColorSwatchHandle(optionValue.value)}",
                listOf(
                    MetaobjectField("label", optionValue.value),
                    MetaobjectField("color", color.toHex()),
                    MetaobjectField("color_taxonomy_reference", "[\"${taxonomy.id}\"]"),
                    MetaobjectField("pattern_taxonomy_reference", "gid://shopify/TaxonomyValue/2874")
                )
            )
            metaobjectClient.create(metaobject)
            optionValue.linkedMetafieldValue = metaobject.id
        }
    }

    fun unorganizedProductImages(product: ShopifyProduct) =
        product.media.filter { !it.fileName.isAnyShopifyImage(product) }

    suspend fun reorganizeProductImages(product: ShopifyProduct, mediaToReorganize: List<ShopifyMedia>) {
        require(product.hasOnlyDefaultVariant || product.variants.all { it.sku.isNotEmpty() }) { "All product variants must have SKU" }

        val images = normalizeProductImages(product, mediaToReorganize)
        val imagesWithShopifyName = images.associate { it.path to it.toShopifyImageFileName(product) }
        val medias = mediaClient.upload(imagesWithShopifyName)
        val variants = images.zip(medias)
            .mapNotNull { (image, media) ->
                val variant = image.variantSku?.let { product.findVariantBySku(it) }
                media.altText = generateAltText(product, variant)
                variant.also { it?.mediaId = media.id }
            }

        mediaClient.update(medias, referencesToAdd = listOf(product.id))
        if (variants.isNotEmpty()) variantClient.update(product, variants)
        mediaClient.update(mediaToReorganize, referencesToRemove = listOf(product.id))

        product.media -= mediaToReorganize.toSet()
        product.media += medias
    }

    fun locallyMissingProductImages(product: ShopifyProduct): List<ShopifyMedia> {
        val knownImages = findAllImages(product).map { it.toShopifyImageFileName(product) }
        return product.media.filter { media -> media.fileName.isAnyShopifyImage(product) && media.fileName !in knownImages }
    }

    suspend fun downloadLocallyMissingProductImages(product: ShopifyProduct, mediaToDownload: List<ShopifyMedia>) {
        val directory = properties.imageDirectory.resolve(product.vendor).resolve(product.productNameForImages)
        directory.createDirectories()
        mediaToDownload.forEachParallel(properties.processingThreads) { media ->
            logger.info { "Downloading image ${media.fileName}" }
            val path = directory.resolve(media.fileName)
            genericWebClient.downloadFileTo(media.src, path)
        }
    }

    fun remotelyMissingProductImages(product: ShopifyProduct): List<SyncImage> {
        val knownImages = findAllImages(product)
        return knownImages.filter { image -> product.media.none { it.fileName == image.toShopifyImageFileName(product) } }
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
        val index = if (variant == null) findNextProductImageNumber(product, indexOfNonVariantImage) else -1
        val fileNameWithoutExtension = variant?.sku?.toFileNamePart() ?: "$PRODUCT_IMAGE_PREFIX-${index}"
        val fileName = "$fileNameWithoutExtension.${media.src.extension}"
        var path = properties.imageDirectory.resolve(product.vendor).resolve(product.productNameForImages).resolve(fileName)
        if (!path.exists()) {
            path.parent.createDirectories()
            genericWebClient.downloadFileTo(media.src, path)
        }

        if (media.src.extension == "webp") {
            val newFileName = "$fileNameWithoutExtension.png"
            val newPath = path.parent.resolve(newFileName)
            val result = Runtime.getRuntime().exec(arrayOf("convert", path.toString(), newPath.toString())).waitFor()
            require(result == 0) { "Conversion of webp to png failed" }
            path.deleteExisting()
            path = newPath
        }
        return SyncImage(path, index, variant?.sku)
    }

    private fun findNextProductImageNumber(product: ShopifyProduct, index: AtomicInt): Int {
        while (true) {
            val candidate = index.andIncrement
            val regex = Regex("${escape(toProductImageNameWithoutExtension(product, candidate))}\\.$EXTENSIONS_PATTERN")
            if (product.media.none { regex.matches(it.fileName) }) return candidate
        }
    }

    private fun generateColorSwatchHandle(productName: String) =
        UMLAUT_REPLACEMENTS
            .fold(productName) { value, (regex, replacement) -> value.replace(regex, replacement) }
            .replace(" ", "-")
            .lowercase()

    private fun generateAltText(product: ShopifyProduct, variant: ShopifyProductVariant? = null) =
        "${product.title} ${variant?.let { "in ${product.options[0].name} ${it.options[0].value}" } ?: "Produktbild"}"
}

private val ShopifyProduct.productNameForImages get() = title.productNameForImages

private fun String.isProductShopifyImage(product: ShopifyProduct): Boolean {
    val prefix = arrayOf(product.vendor, product.productNameForImages).joinToString("-") { it.toFileNamePart() }
    return Regex("${escape(prefix)}-$PRODUCT_IMAGE_PREFIX-[0-9]+\\.$EXTENSIONS_PATTERN").matches(this)
}

private fun String.isVariantShopifyImage(product: ShopifyProduct, variant: ShopifyProductVariant): Boolean {
    val prefix = arrayOf(product.vendor, product.productNameForImages, variant.sku, variant.title).joinToString("-") { it.toFileNamePart() }
    return Regex("${escape(prefix)}\\.$EXTENSIONS_PATTERN").matches(this)
}

private fun String.isAnyShopifyImage(product: ShopifyProduct) =
    isProductShopifyImage(product) || product.variants.any { isVariantShopifyImage(product, it) }

private fun toProductImageNameWithoutExtension(product: ShopifyProduct, index: Int) =
    "${product.vendor.toFileNamePart()}-${product.productNameForImages.toFileNamePart()}-$PRODUCT_IMAGE_PREFIX-$index"

private fun toVariantImageNameWithoutExtension(product: ShopifyProduct, variant: ShopifyProductVariant) =
    arrayOf(product.vendor, product.productNameForImages, variant.sku, variant.title).joinToString("-") { it.toFileNamePart() }

private fun toVariantImageNameWithoutExtension(product: ShopifyProduct, sku: String) =
    toVariantImageNameWithoutExtension(product, product.findVariantBySku(sku)!!)

private fun SyncImage.toShopifyImageFileName(product: ShopifyProduct) =
    if (variantSku == null) "${toProductImageNameWithoutExtension(product, index)}.${path.extension}"
    else "${toVariantImageNameWithoutExtension(product, variantSku)}.${path.extension}"

private val UMLAUT_REPLACEMENTS = listOf(
    """[Ää]+""".toRegex() to "ae",
    """[Öö]+""".toRegex() to "oe",
    """[Üü]+""".toRegex() to "ue",
    """ß+""".toRegex() to "ss"
)