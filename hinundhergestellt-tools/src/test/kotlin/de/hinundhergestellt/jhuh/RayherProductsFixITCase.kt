package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.core.ifDirty
import de.hinundhergestellt.jhuh.vendors.rayher.csv.RayherProduct
import de.hinundhergestellt.jhuh.vendors.rayher.csv.readRayherProducts
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToFlux
import reactor.core.scheduler.Schedulers
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

private val downloadsDirectory = Path(System.getProperty("user.home")).resolve("Downloads")

private val IMAGE_FILE_NAME_REPLACEMENTS = listOf(
    """[Ää]+""".toRegex() to "ae",
    """[Öö]+""".toRegex() to "oe",
    """[Üü]+""".toRegex() to "ue",
    """ß+""".toRegex() to "ss",
    """[^A-Za-z0-9 -]+""".toRegex() to "",
    """^\s+""".toRegex() to "",
    """\s+$""".toRegex() to "",
    """\s+""".toRegex() to "-"
)

@SpringBootTest
@Disabled("Only run manually")
class RayherProductsFixITCase {

    @Autowired
    private lateinit var artooProductClient: ArtooProductClient

    @Autowired
    private lateinit var artooProductGroupClient: ArtooProductGroupClient

    private val imageWebClient = WebClient.builder()
        .codecs { it.defaultCodecs().maxInMemorySize(5 * 1024 * 1024) }
        .build()

    @Test
    fun fixRayherSilikonformen() = runBlocking {
        val rayherProducts = readRayherProducts(downloadsDirectory.resolve("Rayher0725d_Excel.csv"))

        val productGroup = artooProductGroupClient.findAll().first { it.name == "Rayher Silikonformen" }
        artooProductClient.findAll()
            .filter { it.productGroupId == productGroup.id }
            .collect { product ->
                val itemNumber = fixItemNumber(product.itemNumber!!).also { product.itemNumber = it }
                val rayherProduct = rayherProducts.firstOrNull { it.articleNumber == itemNumber }!!
                product.name = rayherProduct.descriptions[0]
                product.description = rayherProduct.description
                    .replace(", SB-Box 1Stück", "")
                    .replace(", Box 1Stück", "")

                product.ifDirty {
                    logger.info { "${it.name} (${it.itemNumber})" }
                    logger.info { "    ${it.description}" }
                    artooProductClient.update(it)
                }
            }
    }

    @Test
    fun downloadProductImages() = runBlocking {
        val rayherProducts = readRayherProducts(workDirectory.resolve("var/Rayher0725d_Excel.csv"))
        artooProductClient.findAll()
//            .filter { it.itemNumber == "88587000" }
            .mapNotNull { product -> product.barcode?.let { barcode -> rayherProducts.firstOrNull { it.ean == barcode } } }
            .collect { rayherProduct ->
                val imageDirectory = workDirectory.resolve("var/images/Rayher ${rayherProduct.descriptions[0]}")
                imageDirectory.createDirectories()

                rayherProduct.imageUrls.forEach { imageUrl ->
                    val imagePath = imageDirectory.resolve(generateImageFileName(rayherProduct, imageUrl))
                    try {
                        logger.info { "Downloading ${imagePath.fileName}" }
                        downloadFileTo(imageUrl, imagePath)
                    } catch (e: WebClientResponseException) {
                        logger.error { "Error downloading $imageUrl: ${e.message}" }
                    }
                }
            }
    }

    private suspend fun downloadFileTo(url: String, target: Path) {
        var success = false
        try {
            target.outputStream().use { output ->
                val body = imageWebClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToFlux<DataBuffer>()
                    .publishOn(Schedulers.boundedElastic())
                DataBufferUtils.write(body, output)
                    .doOnNext { DataBufferUtils.release(it) }
                    .then()
                    .awaitSingleOrNull()
            }
            success = true
        } finally {
            if (!success) {
                target.deleteIfExists()
            }
        }
    }

    private fun fixItemNumber(itemNumber: String) = itemNumber.replace("-", "")

    private fun generateImageFileName(product: RayherProduct, imageUrl: String): String {
        val fileName = URI(imageUrl).path.substringAfterLast("/")
        val extension = fileName.substringAfterLast(".")
        val productNamePart = IMAGE_FILE_NAME_REPLACEMENTS
            .fold(product.descriptions[0]) { value, (regex, replacement) -> value.replace(regex, replacement) }
            .lowercase()
        return "rayher-${productNamePart}-${product.articleNumber}-${extractImageFileNameSuffix(fileName)}.$extension"
    }

    private fun extractImageFileNameSuffix(fileName: String): String {
        val stem = fileName.substringBeforeLast(".")
        val type = stem.substringAfterLast("_").lowercase()
        val index = stem.substringBeforeLast("_").substringAfterLast("-", "").trimStart('0')
        return type + (index.takeIf { it.isNotEmpty() }?.let { "-$it" } ?: "")
    }
}