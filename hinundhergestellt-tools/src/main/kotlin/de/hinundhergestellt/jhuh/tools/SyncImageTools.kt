package de.hinundhergestellt.jhuh.tools

import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.text.Regex.Companion.escape

@Component
class SyncImageTools(
    private val imageDirectoryService: ImageDirectoryService
) {
    fun findProductImages(vendorName: String, productName: String): List<SyncImage> {
        require(productName.isNotEmpty()) { "productName must not be empty" }
        return imageDirectoryService.listDirectoryEntries(Path(vendorName, productName))
            .asSequence()
            .filter { it.isProductSyncImage() }
            .map { SyncImage(it, it.nameWithoutExtension.substringAfterLast("-").toInt(), null) }
            .sortedBy { it.index }
            .toList()
    }

    fun findVariantImages(vendorName: String, productName: String, skus: List<String>): List<SyncImage> {
        if (skus.isEmpty()) return listOf()
        return imageDirectoryService.listDirectoryEntries(Path(vendorName, productName))
            .asSequence()
            .mapNotNull { entry -> skus.firstOrNull { entry.isVariantSyncImage(it) }?.let { entry to it } }
            .map { (entry, sku) -> SyncImage(entry, -1, sku) }
            .toList()
    }

    fun findAllImages(vendorName: String, productName: String, variantSkus: List<String>) =
        findProductImages(vendorName, productName) + findVariantImages(vendorName, productName, variantSkus)
}

data class SyncImage(
    val path: Path,
    val index: Int,
    val variantSku: String?
)

const val PRODUCT_IMAGE_PREFIX = "produktbild"
const val EXTENSIONS_PATTERN = "(png|jpg)"

val String.productNameForImages get() = replace("/", " ")
val URI.extension get() = path.substringAfterLast(".", "")

private fun Path.isProductSyncImage() = Regex("${escape(PRODUCT_IMAGE_PREFIX)}-[0-9]+\\.$EXTENSIONS_PATTERN").matches(name)
private fun Path.isVariantSyncImage(sku: String) = Regex("${escape(sku.toFileNamePart())}\\.$EXTENSIONS_PATTERN").matches(name)

fun String.toFileNamePart() =
    FILE_NAME_REPLACEMENTS
        .fold(this) { value, (regex, replacement) -> value.replace(regex, replacement) }
        .lowercase()

private val UMLAUT_REPLACEMENTS = listOf(
    """[Ää]+""".toRegex() to "ae",
    """[Öö]+""".toRegex() to "oe",
    """[Üü]+""".toRegex() to "ue",
    """ß+""".toRegex() to "ss"
)

private val FILE_NAME_REPLACEMENTS = listOf(
    *UMLAUT_REPLACEMENTS.toTypedArray(),
    """[^A-Za-z0-9 -]+""".toRegex() to "",
    """^\s+""".toRegex() to "",
    """\s+$""".toRegex() to "",
    """\s+""".toRegex() to "-"
)
