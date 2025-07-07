package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.vendors.rayher.csv.readRayherProducts
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ByteArrayResource
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

@SpringBootTest
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
        val rayherProducts = readRayherProducts(Path("/home/lordjaxom/Downloads/Rayher0725d_Excel.csv"))

        val productGroup = artooProductGroupClient.findAll().first { it.name == "Rayher Silikonformen" }
        artooProductClient.findAll()
            .filter { it.productGroupId == productGroup.id }
            .collect { product ->
                val itemNumber = product.itemNumber?.run {
                    if (contains("-")) replace("-", "").also { product.itemNumber = it }
                    else this
                }!!
                val rayherProduct = rayherProducts.firstOrNull { it.articleNumber == itemNumber }!!

                logger.info { "${product.name} -> ${rayherProduct.descriptions[0]}" }
                logger.info { "    ${rayherProduct.description}" }
                logger.info { "    ${rayherProduct.imageUrls}" }

                val imageDirName = "Rayher ${rayherProduct.descriptions[0]}"
                val imagePath = Path("/home/lordjaxom/Dokumente/Hin-undHergestellt/Shopify").resolve(imageDirName)
                imagePath.createDirectories()
                rayherProduct.imageUrls.forEach { imageUrl ->
                    val fileName = URI(imageUrl).path.substringAfterLast("/")
                    val filePath = imagePath.resolve(fileName)
                    if (filePath.exists()) return@forEach
                    imageWebClient.get()
                        .uri(imageUrl)
                        .exchangeToFlux { it.bodyToFlux<ByteArrayResource>() }
                        .collect { Files.write(filePath, it.byteArray) }
                }
            }
    }
}