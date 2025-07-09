package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.core.ifDirty
import de.hinundhergestellt.jhuh.vendors.rayher.csv.readRayherProducts
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

private val downloadsDirectory = Path(System.getProperty("user.home")).resolve("Downloads")

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
        val rayherProducts = readRayherProducts(downloadsDirectory.resolve("Rayher0725d_Excel.csv"))

        val productGroup = artooProductGroupClient.findAll().first { it.name == "Rayher Silikonformen" }
        artooProductClient.findAll()
            .filter { it.productGroupId == productGroup.id }
            .collect { product ->
                val itemNumber = fixItemNumber(product.itemNumber!!)
                val rayherProduct = rayherProducts.firstOrNull { it.articleNumber == itemNumber }!!

                val imageDirName = "Rayher ${rayherProduct.descriptions[0]}"
                val imagePath = Path("/home/lordjaxom/Dokumente/Hin-undHergestellt/Shopify").resolve(imageDirName)
                imagePath.createDirectories()
                rayherProduct.imageUrls.forEach { imageUrl ->
                    val fileName = URI(imageUrl).path.substringAfterLast("/")
                    val filePath = imagePath.resolve(fileName)
                    if (filePath.exists()) return@forEach
                    imageWebClient.get()
                        .uri(imageUrl)
                        // TODO error handling (404 leads to HTML file)
                        .exchangeToFlux { it.bodyToFlux<ByteArrayResource>() }
                        .collect { Files.write(filePath, it.byteArray) }
                }
            }
    }

    private fun fixItemNumber(itemNumber: String) =
        if (itemNumber.contains("-")) itemNumber.replace("-", "")
        else itemNumber
}