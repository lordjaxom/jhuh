package de.hinundhergestellt.jhuh.tools

import de.hinundhergestellt.jhuh.HuhProperties
import org.springframework.stereotype.Component
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

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
            .map { it to generateImageFileName(productName, it.generateImageFileSuffix(), "*") }
            .mapNotNull { (sku, glob) -> productDirectory.listDirectoryEntries(glob).firstOrNull()?.let { sku to it } }
            .map { (sku, path) -> SyncImage(path, sku) }

        return (productImages + variantImages).toList()
    }
}

class SyncImage(
    val path: Path,
    val variantSku: String?
)