package de.hinundhergestellt.jhuh.usecases.products

import de.hinundhergestellt.jhuh.HuhProperties
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.core.forEachIndexedParallel
import de.hinundhergestellt.jhuh.tools.SyncImage
import de.hinundhergestellt.jhuh.tools.SyncImageTools
import de.hinundhergestellt.jhuh.tools.downloadFileTo
import de.hinundhergestellt.jhuh.tools.extension
import de.hinundhergestellt.jhuh.tools.generateImageFileName
import de.hinundhergestellt.jhuh.tools.syncImageProductName
import de.hinundhergestellt.jhuh.vendors.rayher.csv.RayherProduct
import de.hinundhergestellt.jhuh.vendors.rayher.datastore.RayherDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientException
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectories

private val logger = KotlinLogging.logger { }

@Service
class EditProductService(
    private val mappingService: MappingService,
    private val shopTexterService: ShopTexterService,
    private val rayherDataStore: RayherDataStore,
    private val syncVendorRepository: SyncVendorRepository,
    private val syncImageTools: SyncImageTools,
    private val toolsWebClient: WebClient,
    private val properties: HuhProperties
) {
    val vendors get() = syncVendorRepository.findAll()

    fun inheritedTags(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct) =
        mappingService.inheritedTags(syncProduct, artooProduct).toMutableSet()

    fun sanitizeTag(tag: String) = mappingService.sanitizeTag(tag)

    fun generateProductDetails(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct, description: String?) =
        shopTexterService.generateProductDetails(artooProduct, syncProduct, description)

    fun generateProductTags(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct, description: String?) =
        shopTexterService.generateProductTags(artooProduct, syncProduct, description)

    fun canFillInValues(artooProduct: ArtooMappedProduct) =
        findRayherProduct(artooProduct) != null

    fun fillInValues(artooProduct: ArtooMappedProduct): FilledInProductValues {
        val rayherProduct = findRayherProduct(artooProduct)
        if (rayherProduct != null) {
            return FilledInProductValues(
                vendor = syncVendorRepository.findByNameIgnoreCase("Rayher")!!,
                description = rayherProduct.description,
                weight = rayherProduct.weight
            )
        }

        return FilledInProductValues()
    }

    fun findSyncImages(artooProduct: ArtooMappedProduct, description: String?): List<SyncImage> {
        val productTitle = description?.takeIf { it.isNotEmpty() }
            ?: artooProduct.description.takeIf { it.isNotEmpty() }
            ?: return listOf()
        return syncImageTools.findSyncImages(productTitle.syncImageProductName, artooProduct.variations.mapNotNull { it.itemNumber })
    }

    fun canDownloadImages(artooProduct: ArtooMappedProduct, description: String?) =
        (findRayherProduct(artooProduct)?.imageUrls?.isNotEmpty() ?: false) &&
                (!description.isNullOrEmpty() || artooProduct.description.isNotEmpty())

    suspend fun downloadImages(artooProduct: ArtooMappedProduct, description: String?, report: suspend (String) -> Unit) {
        val rayherProduct = findRayherProduct(artooProduct)
        if (rayherProduct != null) {
            downloadRayherImages(artooProduct, description, rayherProduct, report)
        }
    }

    private fun findRayherProduct(artooProduct: ArtooMappedProduct) =
        if (artooProduct.hasOnlyDefaultVariant) artooProduct.variations[0].barcode?.let { rayherDataStore.findByEan(it) }
        else null

    private suspend fun downloadRayherImages(
        artooProduct: ArtooMappedProduct,
        description: String?,
        rayherProduct: RayherProduct,
        report: suspend (String) -> Unit
    ) {
        val productTitle = description?.takeIf { it.isNotEmpty() }
            ?: artooProduct.description.takeIf { it.isNotEmpty() }
            ?: return
        val productName = productTitle.syncImageProductName
        val productPath = properties.imageDirectory.resolve(productName)
        productPath.createDirectories()

        report("Lade ${rayherProduct.imageUrls.size} Produktbilder herunter...")
        rayherProduct.imageUrls
            .sortedBy { it.computeRayherSortSelector() }
            .forEachIndexedParallel(properties.processingThreads) { index, imageUrl ->
                downloadRayherImage(productName, productPath, index, imageUrl)
            }
    }

    private suspend fun downloadRayherImage(productName: String, productPath: Path, index: Int, imageUrl: String) {
        val extension = URI(imageUrl).extension
        val suffix = "produktbild-${index + 1}"
        val fileName = generateImageFileName(productName, suffix, extension)
        val filePath = productPath.resolve(fileName)

        try {
            toolsWebClient.downloadFileTo(imageUrl, filePath)
        } catch (e: WebClientException) {
            logger.error(e) { "Couldn't download image $fileName from Rayher" }
        }
    }

    private fun String.computeRayherSortSelector(): String {
        val stem = substringBeforeLast(".")
        val discrimitator = when {
            stem.endsWith("_VP") -> 1
            stem.endsWith("_PF") -> 2
            stem.endsWith("_DI") -> 3
            stem.endsWith("_AL") -> 4
            else -> 9
        }
        return "$discrimitator:$this"
    }
}

class FilledInProductValues(
    val vendor: SyncVendor? = null,
    val description: String? = null,
    val weight: BigDecimal? = null
)
