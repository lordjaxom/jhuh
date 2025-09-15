package de.hinundhergestellt.jhuh.tools

import de.hinundhergestellt.jhuh.HuhProperties
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.text.Regex.Companion.escape

@Component
class SyncImageTools(
    private var properties: HuhProperties
) {
    fun findSyncImages(productName: String, variantSkus: List<String> = listOf()): List<SyncImage> {
        require(productName.isNotEmpty()) { "productName must not be empty" }

        val productDirectory = properties.imageDirectory.resolve(productName).takeIf { it.isDirectory() } ?: return listOf()

        val productImages = productDirectory
            .listDirectoryEntries(generateImageFileName(productName, "produktbild-*", "*"))
            .asSequence()
            .sortedBy { it.computeImageSortSelector() }
            .map { SyncImage(it, null) }

        val variantImages = variantSkus
            .asSequence()
            .map { it to generateImageFileName(productName, it.syncImageSuffix, "*") }
            .mapNotNull { (sku, glob) -> productDirectory.listDirectoryEntries(glob).firstOrNull()?.let { sku to it } }
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

fun generateImageFileName(productName: String, suffix: String, extension: String): String {
    val productNamePart = IMAGE_FILE_NAME_REPLACEMENTS
        .fold(productName) { value, (regex, replacement) -> value.replace(regex, replacement) }
        .lowercase()
    return "${productNamePart}-${suffix}.$extension"
}


fun String.isValidSyncImageFor(productName: String, variantSkus: List<String> = listOf()): Boolean {
    return Regex(generateImageFileName(productName, "produktbild-[0-9]+", "(png|jpg)")).matches(this) ||
            variantSkus.any { isValidSyncImageFor(productName, it) }
}

private fun String.isValidSyncImageFor(productName: String, variantSku: String) =
    Regex(generateImageFileName(productName, escape(variantSku.syncImageSuffix), EXTENSIONS)).matches(this)


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
