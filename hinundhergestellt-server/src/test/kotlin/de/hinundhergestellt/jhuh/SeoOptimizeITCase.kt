package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.hinundhergestellt.jhuh.tools.productNameForImages
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.reflect.KProperty1
import kotlin.test.Test

@SpringBootTest
class SeoOptimizeITCase {

    @Autowired
    private lateinit var productClient: ShopifyProductClient

    @Autowired
    private lateinit var artooDataStore: ArtooDataStore

    @Test
    fun optimizeProductSEO() = runBlocking {
        val products = productClient.fetchAll().toList()
        val patches = jacksonObjectMapper().readValue<List<ProductPatch>>(Path("/home/lordjaxom/Dokumente/products-fixed.json").toFile())
        val seen = mutableSetOf(
            "poli-flex®-pearl-glitter-flexfolie-21-cm-x-29-7-cm",
            "poli-flex®-turbo-flexfolie-21-cm-x-29-7-cm",
            "poli-tack-8031-pet-transferfolie-21-cm-x-29-7-cm"
        )
        patches.forEach { patch ->
            if (seen.contains(patch.oldHandle)) return@forEach
            seen.add(patch.oldHandle)

            val other = patches.asSequence().filter { it.oldHandle == patch.oldHandle }.toList()
            val product = products.firstOrNull() { it.handle == patch.oldHandle } ?: throw NoSuchElementException(patch.oldHandle)

            val handle = latest(other, ProductPatch::handle)
            if (!handle.isNullOrEmpty() && handle != product.handle) {
                println("Handle: ${product.handle} to $handle")
                product.handle = handle
            }
            val title = latest(other, ProductPatch::title)
            if (!title.isNullOrEmpty() && title != product.title) {
                val oldFolder = Path("/media/lordjaxom/akv-soft.de/sascha/Hin- und Hergestellt/Shopify")
                    .resolve(product.vendor)
                    .resolve(product.title.productNameForImages)
                if (oldFolder.exists()) {
                    val newFolder = Path("/media/lordjaxom/akv-soft.de/sascha/Hin- und Hergestellt/Shopify")
                        .resolve(product.vendor)
                        .resolve(title.productNameForImages)
                    println("Renaming image folder: $oldFolder to $newFolder")
                    oldFolder.moveTo(newFolder)
                }

                val artoo = artooDataStore.findAllProducts().first { it.barcodes.any { barcode -> product.findVariantByBarcode(barcode) != null } }
                println("Updating Artoo product name: ${artoo.description} to $title")
                artoo.description = title
                artooDataStore.update(artoo)

                println("Title: ${product.title} to $title")
                product.title = title
            }
            val seoTitle = latest(other, ProductPatch::seoTitle)
            if (!seoTitle.isNullOrEmpty() && seoTitle != product.seoTitle) {
                println("SEO Title: ${product.seoTitle} to $seoTitle")
                product.seoTitle = seoTitle
            }
            val seoDescription = latest(other, ProductPatch::seoDescription)
            if (!seoDescription.isNullOrEmpty() && seoDescription != product.seoDescription) {
                println("SEO Description: ${product.seoDescription} to $seoDescription")
                product.seoDescription = seoDescription
            }

            val shortDescription = latest(other, ProductPatch::shortDescription)
            if (!shortDescription.isNullOrEmpty()) {
                product.descriptionHtml = "<p>$shortDescription</p>\n" + product.descriptionHtml
            }

            productClient.update(product)
            return@runBlocking
        }
    }
}

class ProductPatch(
    val oldHandle: String,
    val handle: String,
    val title: String,
    val technicalDetails: Map<String, String>?,
    val seoTitle: String,
    val seoDescription: String,
    val shortDescription: String,
)

private fun <T> latest(patches: List<ProductPatch>, property: KProperty1<ProductPatch, T>): T? {
    return patches.asReversed().asSequence()
        .map { property.get(it) }
        .firstOrNull { it != null && (it !is String || it.isNotEmpty()) }
}