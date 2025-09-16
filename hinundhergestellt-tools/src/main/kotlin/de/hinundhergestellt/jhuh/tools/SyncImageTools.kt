package de.hinundhergestellt.jhuh.tools

import de.hinundhergestellt.jhuh.HuhProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.text.Regex.Companion.escape

@Component
class SyncImageTools(
    private var properties: HuhProperties
) {
    fun findSyncImages(productName: String, variantSkus: List<String> = listOf()): List<SyncImage> {
        require(productName.isNotEmpty()) { "productName must not be empty" }

        val productDirectory = properties.imageDirectory.resolve(productName).takeIf { it.isDirectory() } ?: return listOf()

        val productImages = productDirectory
            .listDirectoryEntries(generateImageFileName(productName, "$PRODUCT_SUFFIX-*", "*"))
            .asSequence()
            .filter { it.fileName.toString().isValidSyncImageFor(productName, variantSkus) }
            .sortedBy { it.syncImageSortSelector }
            .map { SyncImage(it, null) }

        val variantImages = variantSkus
            .asSequence()
            .map { it to generateImageFileName(productName, it.syncImageSuffix, "*") }
            .mapNotNull { (sku, glob) ->
                productDirectory
                    .listDirectoryEntries(glob)
                    .asSequence()
                    .filter { it.fileName.toString().isValidSyncImageFor(productName, sku) }
                    .firstOrNull()
                    ?.let { sku to it }
            }
            .map { (sku, path) -> SyncImage(path, sku) }

        return (productImages + variantImages).toList()
    }
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
    return Regex(generateImageFileName(productName, "produktbild-[0-9]+", EXTENSIONS)).matches(this) ||
            variantSkus.any { isValidSyncImageFor(productName, it) }
}

fun String.isValidSyncImageFor(productName: String, variantSku: String) =
    Regex(generateImageFileName(productName, escape(variantSku.syncImageSuffix), EXTENSIONS)).matches(this)

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
