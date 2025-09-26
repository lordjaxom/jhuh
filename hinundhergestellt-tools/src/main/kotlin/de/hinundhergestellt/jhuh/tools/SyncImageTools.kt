package de.hinundhergestellt.jhuh.tools

import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.text.Regex.Companion.escape

@Component
class SyncImageTools(
    private val imageDirectoryService: ImageDirectoryService
) {
    fun findProductImages(productName: String): List<SyncImage> {
        require(productName.isNotEmpty()) { "productName must not be empty" }
        return imageDirectoryService.listDirectoryEntries(Path(productName))
            .asSequence()
            .filter { it.isValidSyncImageFor(productName) }
            .sortedBy { it.syncImageSortSelector }
            .map { SyncImage(it, null) }
            .toList()
    }

    fun findVariantImages(productName: String, variantSkus: List<String>): List<SyncImage> {
        if (variantSkus.isEmpty()) return listOf()
        return imageDirectoryService.listDirectoryEntries(Path(productName))
            .asSequence()
            .mapNotNull { entry -> variantSkus.firstOrNull { entry.isValidSyncImageFor(productName, it) }?.let { SyncImage(entry, it) } }
            .toList()
    }

    fun findAllImages(productName: String, variantSkus: List<String>) =
        findProductImages(productName) + findVariantImages(productName, variantSkus)
}

class SyncImage(
    val path: Path,
    val variantSku: String?
)

val String.syncImageProductName get() = substringBefore(",")
val String.syncImageSuffix get() = replace(" ", "-").lowercase() // TODO: remove unwanted characters

val URI.extension get() = path.substringAfterLast(".", "")

val Path.syncImageSortSelector
    get() = Regex("""$PRODUCT_SUFFIX-(\d+)$""").find(nameWithoutExtension)
        ?.let { "1:${it.groupValues[1].padStart(4, '0')}" }
        ?: "2:$nameWithoutExtension"

fun String.isValidSyncImageFor(productName: String, variantSkus: List<String> = listOf()): Boolean {
    return Regex(generateImageFileName(productName, "$PRODUCT_SUFFIX-[0-9]+", EXTENSIONS)).matches(this) ||
            variantSkus.any { isValidSyncImageFor(productName, it) }
}

fun Path.isValidSyncImageFor(productName: String, variantSkus: List<String> = listOf()) =
    fileName.toString().isValidSyncImageFor(productName, variantSkus)

fun String.isValidSyncImageFor(productName: String, variantSku: String) =
    Regex(generateImageFileName(productName, escape(variantSku.syncImageSuffix), EXTENSIONS)).matches(this)

fun Path.isValidSyncImageFor(productName: String, variantSku: String) =
    fileName.toString().isValidSyncImageFor(productName, variantSku)

fun generateImageFileName(productName: String, suffix: String, extension: String): String {
    val productNamePart = IMAGE_FILE_NAME_REPLACEMENTS
        .fold(productName) { value, (regex, replacement) -> value.replace(regex, replacement) }
        .lowercase()
    return "${productNamePart}-${suffix}.$extension"
}

private const val PRODUCT_SUFFIX = "produktbild"
private const val EXTENSIONS = "(png|jpg)"

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
