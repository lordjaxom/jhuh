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
import de.hinundhergestellt.jhuh.tools.variantSkus
import de.hinundhergestellt.jhuh.vendors.hobbyfun.datastore.HobbyFunDataStore
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
    private val hobbyFunDataStore: HobbyFunDataStore,
    private val syncVendorRepository: SyncVendorRepository,
    private val syncImageTools: SyncImageTools,
    private val toolsWebClient: WebClient,
    private val properties: HuhProperties
) {
    val vendors get() = syncVendorRepository.findAll()

    fun inheritedTags(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct) =
        mappingService.inheritedTags(syncProduct, artooProduct).toMutableSet()

    fun sanitizeTag(tag: String) = mappingService.sanitizeTag(tag)

    fun generateProductTexts(artoo: ArtooMappedProduct, sync: SyncProduct, description: String?) =
        shopTexterService.generateProductTexts(artoo, sync, description)

    fun generateProductDetails(artoo: ArtooMappedProduct, sync: SyncProduct, description: String?) =
        shopTexterService.generateProductDetails(artoo, sync, description)

    fun canFillInValues(artooProduct: ArtooMappedProduct) =
        findRayherProduct(artooProduct) != null || findHobbyFunProduct(artooProduct) != null

    fun fillInValues(artooProduct: ArtooMappedProduct): FilledInProductValues {
        val rayherProduct = findRayherProduct(artooProduct)
        if (rayherProduct != null) {
            return FilledInProductValues(
                vendor = syncVendorRepository.findByNameIgnoreCase("Rayher")!!,
                description = rayherProduct.description,
                weight = rayherProduct.weight
            )
        }

        val hobbyFunProduct = findHobbyFunProduct(artooProduct)
        if (hobbyFunProduct != null) {
            return FilledInProductValues(
                vendor = syncVendorRepository.findByNameIgnoreCase("HobbyFun")!!,
                description = hobbyFunProduct.description
            )
        }

        return FilledInProductValues()
    }

    fun findSyncImages(artooProduct: ArtooMappedProduct, description: String?): List<SyncImage> {
        val productTitle = description?.takeIf { it.isNotEmpty() }
            ?: artooProduct.description.takeIf { it.isNotEmpty() }
            ?: return listOf()
        return syncImageTools.findAllImages(productTitle.syncImageProductName, artooProduct.variantSkus)
    }

    fun canDownloadImages(artooProduct: ArtooMappedProduct, description: String?) =
        (!description.isNullOrEmpty() || artooProduct.description.isNotEmpty()) &&
                (findRayherProduct(artooProduct)?.imageUrls?.isNotEmpty() == true ||
                        findHobbyFunProduct(artooProduct)?.imageUrl?.isNotEmpty() == true)

    suspend fun downloadImages(artooProduct: ArtooMappedProduct, description: String?, report: suspend (String) -> Unit) {
        report("Suche Produkt in Katalogen...")

        val rayherProduct = findRayherProduct(artooProduct)
        if (rayherProduct != null) {
            downloadProductImages(artooProduct, description, rayherProduct.imageUrls, report)
            return
        }

        val hobbyFunProduct = findHobbyFunProduct(artooProduct)
        if (hobbyFunProduct != null) {
            downloadProductImages(artooProduct, description, listOf(hobbyFunProduct.imageUrl), report)
        }
    }

    private fun findRayherProduct(artooProduct: ArtooMappedProduct) =
        if (artooProduct.hasOnlyDefaultVariant) artooProduct.variations[0].barcode?.let { rayherDataStore.findByEan(it) }
        else null

    private fun findHobbyFunProduct(artooProduct: ArtooMappedProduct) =
        if (artooProduct.hasOnlyDefaultVariant) artooProduct.variations[0].barcode?.let { hobbyFunDataStore.findByEan(it) }
        else null

    private suspend fun downloadProductImages(
        artooProduct: ArtooMappedProduct,
        description: String?,
        imageUrls: List<String>,
        report: suspend (String) -> Unit
    ) {
        val productTitle = description?.takeIf { it.isNotEmpty() }
            ?: artooProduct.description.takeIf { it.isNotEmpty() }
            ?: return
        val productName = productTitle.syncImageProductName
        val productPath = properties.imageDirectory.resolve(productName)
        productPath.createDirectories()

        report("Lade ${imageUrls.size} Produktbilder herunter...")
        imageUrls
            .sortedBy { it.computeImageSortSelector() }
            .forEachIndexedParallel(properties.processingThreads) { index, imageUrl ->
                downloadProductImage(productName, productPath, index, imageUrl)
            }
    }

    private suspend fun downloadProductImage(productName: String, productPath: Path, index: Int, imageUrl: String) {
        val extension = URI(imageUrl).extension
        val suffix = "produktbild-${index + 1}"
        val fileName = generateImageFileName(productName, suffix, extension)
        val filePath = productPath.resolve(fileName)

        try {
            toolsWebClient.downloadFileTo(imageUrl, filePath)
        } catch (e: WebClientException) {
            logger.error(e) { "Couldn't download image $fileName from Vendor" }
        }
    }

    private fun String.computeImageSortSelector(): String {
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
