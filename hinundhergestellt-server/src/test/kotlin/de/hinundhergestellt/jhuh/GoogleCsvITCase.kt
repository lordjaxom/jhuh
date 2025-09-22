package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import org.junit.jupiter.api.AutoClose
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.test.Test

@SpringBootTest
class GoogleCsvITCase {

    @Autowired
    private lateinit var shopifyDataStore: ShopifyDataStore

    @Autowired
    private lateinit var syncProductRepository: SyncProductRepository

    @AutoClose
    private val output = Path("/home/lordjaxom/Dokumente/Hin-undHergestellt/local_feed.tsv").bufferedWriter()

    @Test
    fun createGoogleCsv() {
        output.write(listOf("store_code","id","quantity","availability").joinToString("\t"))
        output.newLine()

        shopifyDataStore.products.forEach { product ->
            val productId = product.id.substringAfterLast("/")
            val syncProduct = syncProductRepository.findByShopifyId(product.id) ?: return@forEach
            product.variants.forEach { variant ->
                val variantId = variant.id.substringAfterLast("/")
                if (!syncProduct.variants.any { it.shopifyId == variant.id }) return@forEach
                val csvLine = listOf(
                    "hinundhergestellt",
                    "shopify_ZZ_${productId}_${variantId}",
                    variant.inventoryQuantity?.toString() ?: "0",
                    variant.inventoryQuantity?.takeIf { it > 0 }?.let { "in_stock" } ?: "out_of_stock"
                )
                output.write(csvLine.joinToString("\t"))
                output.newLine()
            }
        }
    }
}