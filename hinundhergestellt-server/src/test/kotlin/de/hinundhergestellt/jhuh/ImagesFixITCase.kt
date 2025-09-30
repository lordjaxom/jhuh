package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.tools.syncImageProductName
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.test.Test

@SpringBootTest
class ImagesFixITCase {

    @Autowired
    private lateinit var artooDataStore: ArtooDataStore

    @Autowired
    private lateinit var syncProductRepository: SyncProductRepository

    @Autowired
    private lateinit var properties: HuhProperties

    @Test
    fun moveImagesToVendorFolder() {
        artooDataStore.findAllProducts().forEach { product ->
            val productName = product.syncImageProductName ?: return@forEach
            val oldPath = properties.imageDirectory.resolve(productName)
            if (!oldPath.exists()) return@forEach

            val syncProduct = syncProductRepository.findByArtooId(product.id)
            val vendorName = syncProduct?.vendor?.name
            if (vendorName == null) {
                println("ERROR: No vendor for product $productName")
                return@forEach
            }

            val newPath = properties.imageDirectory.resolve(vendorName).resolve(productName)
            newPath.parent.createDirectories()
            oldPath.moveTo(newPath)
        }
    }
}