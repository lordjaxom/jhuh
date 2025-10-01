package de.hinundhergestellt.jhuh

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.tools.ImageDirectoryService
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class ImagesFixITCase {

    @Autowired
    private lateinit var artooDataStore: ArtooDataStore

    @Autowired
    private lateinit var syncProductRepository: SyncProductRepository

    @Autowired
    private lateinit var properties: HuhProperties

    @Autowired
    private lateinit var shopifyProductClient: ShopifyProductClient

    @Autowired
    private lateinit var shopifyGraphQLClient: WebClientGraphQLClient

    @MockitoBean
    private lateinit var imageDirectoryServiceMock: ImageDirectoryService
//
//    @Test
//    fun moveImagesToVendorFolder() {
//        artooDataStore.findAllProducts().forEach { product ->
//            val productName = product.syncImageProductName ?: return@forEach
//            val oldPath = properties.imageDirectory.resolve(productName)
//            if (!oldPath.exists()) return@forEach
//
//            val syncProduct = syncProductRepository.findByArtooId(product.id)
//            val vendorName = syncProduct?.vendor?.name
//            if (vendorName == null) {
//                println("ERROR: No vendor for product $productName")
//                return@forEach
//            }
//
//            val newPath = properties.imageDirectory.resolve(vendorName).resolve(productName)
//            newPath.parent.createDirectories()
//            oldPath.moveTo(newPath)
//        }
//    }
//
//    @Test
//    fun removeProductFromImageName() {
//        artooDataStore.findAllProducts().forEach { product ->
//            val syncProduct = syncProductRepository.findByArtooId(product.id) ?: return@forEach
//            val vendorName = syncProduct.vendor?.name ?: return@forEach
//            val productName = product.syncImageProductName ?: return@forEach
//
//            val directory = properties.imageDirectory.resolve(vendorName).resolve(productName)
//            if (!directory.exists()) return@forEach
//
//            val productNamePart = generateImageFileName(productName, "", "").removeSuffix(".")
//            directory.listDirectoryEntries().forEach { entry ->
//                if (entry.nameWithoutExtension.endsWith("-swatch")) return@forEach
//
//                if (!entry.isValidSyncImageFor(productName, product.variantSkus)) {
//                    println("WARN: Orphaned $entry")
//                    return@forEach
//                }
//
//                val newPath = entry.parent.resolve(entry.fileName.toString().removePrefix(productNamePart))
//                entry.moveTo(newPath)
//            }
//        }
//    }
//
//    @Test
//    fun updateShopifyImageFileNames(): Unit = runBlocking {
//        shopifyProductClient.fetchAll().toList()
//            .mapNotNull { product ->
//                val medias = product.media.filter { it.fileName.isValidSyncImageFor(product) }
//                if (medias.isEmpty()) return@mapNotNull null
//
//                val oldPrefix = generateImageFileName(product.syncImageProductName, "", "").removeSuffix(".")
//                val newPrefix = generateImageFileName("${product.vendor} ${product.title}".syncImageProductName, "", "").removeSuffix(".")
//
//                medias.map { media ->
//                    val suffix = product.variants.firstOrNull { it.mediaId == media.id }
//                        ?.let { "-" + generateImageFileName(it.title, "", "").removeSuffix("-.") }
//                        ?: ""
//                    val extension = URI(media.src).extension
//                    media to "$newPrefix${media.fileName.removePrefix(oldPrefix).removeSuffix(".$extension")}$suffix.$extension"
//                }
//            }
//            .flatten()
//            .forEach { (media, fileName) ->
//                val update = FileUpdateInput(id = media.id, filename = fileName)
//                val request = buildMutation {
//                    fileUpdate(listOf(update)) {
//                        userErrors { message; field }
//                    }
//                }
//                try {
//                    shopifyGraphQLClient.executeMutation(request, FileUpdatePayload::userErrors)
//                } catch (ex: Exception) {
//                    println("FAILED: ${media.fileName}")
//                }
//                Unit
//            }
//    }
//
//    @Test
//    fun findFailedImages() = runBlocking {
//        val request = buildQuery {
//            files(first = 100, query = "id:56506470269256") {
//                edges {
//                    node {
//                        id; fileStatus
//                        fileErrors { message }
//                        onMediaImage {
//                            image { src }
//                            mediaErrors { message }
//                            mediaWarnings { message }
//                        }
//                    }
//                }
//            }
//        }
//
//        val payload = shopifyGraphQLClient.executeQuery<FileConnection>(request)
//        println(payload.edges.map { (it.node as? MediaImage)?.image?.src to it.node.fileErrors.map { it.message } })
//    }
}