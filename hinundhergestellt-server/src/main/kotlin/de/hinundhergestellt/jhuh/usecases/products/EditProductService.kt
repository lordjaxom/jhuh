package de.hinundhergestellt.jhuh.usecases.products

import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendorRepository
import de.hinundhergestellt.jhuh.tools.downloadFileTo
import de.hinundhergestellt.jhuh.tools.extractFileExtension
import de.hinundhergestellt.jhuh.tools.extractProductName
import de.hinundhergestellt.jhuh.tools.generateImageFileName
import de.hinundhergestellt.jhuh.vendors.rayher.csv.RayherProduct
import de.hinundhergestellt.jhuh.vendors.rayher.datastore.RayherDataStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.beans.factory.annotation.Value
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
    private val toolsWebClient: WebClient,
    @Value("\${hinundhergestellt.image-directory}") private val imageDirectory: Path,
    @Value("\${hinundhergestellt.download-threads}") private val downloadThreads: Int
) {
    val vendors get() = syncVendorRepository.findAll()

    fun inheritedTags(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct) =
        mappingService.inheritedTags(syncProduct, artooProduct).toMutableSet()

    fun sanitizeTag(tag: String) = mappingService.sanitizeTag(tag)

    fun generateProductDetails(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct) =
        shopTexterService.generateProductDetails(artooProduct, syncProduct)

    fun generateProductTags(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct) =
        shopTexterService.generateProductTags(artooProduct, syncProduct)

    fun canFillInValues(artooProduct: ArtooMappedProduct) = findRayherProduct(artooProduct) != null

    suspend fun fillInValues(artooProduct: ArtooMappedProduct, report: suspend (String) -> Unit): FilledInProductValues {
        val rayherProduct = findRayherProduct(artooProduct)
        if (rayherProduct != null) {
            downloadRayherImages(artooProduct, rayherProduct, report)
            return FilledInProductValues(
                vendor = syncVendorRepository.findByNameIgnoreCase("Rayher")!!,
                description = rayherProduct.description,
                weight = rayherProduct.weight
            )
        }

        return FilledInProductValues()
    }

    private fun findRayherProduct(artooProduct: ArtooMappedProduct) =
        if (artooProduct.hasOnlyDefaultVariant) artooProduct.variations[0].barcode?.let { rayherDataStore.findByEan(it) }
        else null

    private suspend fun downloadRayherImages(
        artooProduct: ArtooMappedProduct,
        rayherProduct: RayherProduct,
        report: suspend (String) -> Unit
    ) {
        val productName = artooProduct.description.extractProductName()
        val productPath = imageDirectory.resolve(productName)
        productPath.createDirectories()

        coroutineScope {
            val semaphore = Semaphore(downloadThreads)
            rayherProduct.imageUrls
                .sortedBy { it.computeRayherSortSelector() }
                .mapIndexed { index, imageUrl ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            report("Downloading image ${index + 1} of ${rayherProduct.imageUrls.size}")
                            downloadRayherImage(productName, productPath, index, imageUrl)
                        }
                    }
                }.awaitAll()
        }
    }

    private suspend fun downloadRayherImage(productName: String, productPath: Path, index: Int, imageUrl: String) {
        val extension = URI(imageUrl).extractFileExtension()
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
        val discrimitator = when  {
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
