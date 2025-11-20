package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.tools.ImageDirectoryService
import de.hinundhergestellt.jhuh.tools.ShopifyImageTools
import de.hinundhergestellt.jhuh.tools.productNameForImages
import de.hinundhergestellt.jhuh.tools.toFileNamePart
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMediaClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import kotlin.collections.chunked
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@Disabled("Only run manually")
class ShopifyProductsFixITCase {

    @Autowired
    private lateinit var shopifyDataStore: ShopifyDataStore

    @Autowired
    private lateinit var artooDataStore: ArtooDataStore

    @Autowired
    private lateinit var shopifyImageTools: ShopifyImageTools

    @Autowired
    private lateinit var shopTexterService: ShopTexterService

    @Autowired
    private lateinit var productClient: ShopifyProductClient

    @Autowired
    private lateinit var mediaClient: ShopifyMediaClient

    @MockitoBean
    private lateinit var imageDirectoryService: ImageDirectoryService

    @Test
    fun reorganizeProducts() = runBlocking {
        shopifyDataStore.products
            .filter {
                it.seoTitle.isNullOrEmpty() && "Gießform" in it.tags
            }
            .forEach {
                reorganizeProduct(it)
                normalizeImagesToUrlHandle(it)
            }
    }

    @Test
    fun normalizeImagesToUrlHandle() = runBlocking {
        shopifyDataStore.products
            .filter {
                it.title in listOf(
                    "myboshi Fellino Kunstfellgarn – 100 g Knäuel",
                    "myboshi No.2 Baumwolle-Kapok Garn – 50 g Knäuel",
                    "myboshi Samt Chenillegarn – 100 g Knäuel",
                )
            }
            .forEach { normalizeImagesToUrlHandle(it) }
    }

    private suspend fun reorganizeProduct(product: ShopifyProduct) {
        val reworked = shopTexterService.reworkProductTexts(product)
        if (reworked.handle != product.handle) {
            println("Handle: ${product.handle} to ${reworked.handle}")
            product.handle = reworked.handle
        }
        if (reworked.title != product.title) {
            val oldFolder = Path("/media/lordjaxom/akv-soft.de/sascha/Hin- und Hergestellt/Shopify")
                .resolve(product.vendor)
                .resolve(product.title.substringBefore(",").replace("/", " "))
            if (oldFolder.exists()) {
                val newFolder = Path("/media/lordjaxom/akv-soft.de/sascha/Hin- und Hergestellt/Shopify")
                    .resolve(product.vendor)
                    .resolve(reworked.title.productNameForImages)
                println("Renaming image folder: $oldFolder to $newFolder")
                oldFolder.moveTo(newFolder)
            }

            val artoo =
                artooDataStore.findAllProducts().first { it.barcodes.any { barcode -> product.findVariantByBarcode(barcode) != null } }
            if (artoo.description != reworked.title) {
                println("Updating Artoo product name: ${artoo.description} to ${reworked.title}")
                artoo.description = reworked.title
                artooDataStore.update(artoo)
            }

            println("Title: ${product.title} to ${reworked.title}")
            product.title = reworked.title
        }
        if (reworked.seoTitle != product.seoTitle) {
            println("SEO Title: ${product.seoTitle} to ${reworked.seoTitle}")
            product.seoTitle = reworked.seoTitle
        }
        if (reworked.seoDescription != product.seoDescription) {
            println("SEO Description: ${product.seoDescription} to ${reworked.seoDescription}")
            product.seoDescription = reworked.seoDescription
        }
        if (reworked.descriptionHtml != product.descriptionHtml) {
            product.descriptionHtml = reworked.descriptionHtml
        }

        productClient.update(product)
    }

    private suspend fun normalizeImagesToUrlHandle(product: ShopifyProduct) {
        val changedMedia = mutableSetOf<ShopifyMedia>()
        product.media.forEach { media ->
            Regex("-produktbild-[0-9]+\\.(png|jpg)$").find(media.fileName)?.also {
                val newFileName = "${product.handle}${it.groupValues[0]}"
                if (media.fileName != newFileName) {
                    println("Renaming image file: '${media.fileName}' to '$newFileName'")
                    media.fileName = newFileName
                    changedMedia.add(media)
                }
                val newAltText = shopifyImageTools.generateAltText(product)
                if (media.altText != newAltText) {
                    println("Updating alt text '${media.altText}' to '$newAltText'")
                    media.altText = newAltText
                    changedMedia.add(media)
                }
            }
        }
        if (!product.hasOnlyDefaultVariant) {
            product.variants.forEach { variant ->
                val variantMedia = product.media.first { variant.mediaId == it.id }
                val newFileName = "${product.handle}-${variant.title.toFileNamePart()}.${variantMedia.fileName.substringAfterLast(".")}"
                if (variantMedia.fileName != newFileName) {
                    println("Renaming variant image file: '${variantMedia.fileName}' to '$newFileName'")
                    variantMedia.fileName = newFileName
                    changedMedia.add(variantMedia)
                }
                val newAltText = shopifyImageTools.generateAltText(product, variant)
                if (variantMedia.altText != newAltText) {
                    println("Updating alt text '${variantMedia.altText}' to '$newAltText'")
                    variantMedia.altText = newAltText
                    changedMedia.add(variantMedia)
                }
            }
        }

        changedMedia.chunked(10).forEach {
            println("Updating next chunk of ${it.size} media items")
            mediaClient.update(it)
        }
    }

    @Test
    fun fixUrlHandle() = runBlocking {
        fixUrlHandle("superior-9500-jewel-metallic-a4", "superior-9500-jewel-metallic-vinylfolie-a4")
    }

    private suspend fun fixUrlHandle(oldHandle: String, newHandle: String) {
        val product = shopifyDataStore.products.first { it.handle == oldHandle }
        product.handle = newHandle
        productClient.update(product)
        normalizeImagesToUrlHandle(product)
    }

    @Test
    fun unorganizedShopifyImages() = runBlocking {
        val product = shopifyDataStore.products.first { it.title == "POLI-FLEX® PEARL GLITTER Flexfolie – A4 Bogen (20×30 cm)" }
        assertEquals(0, shopifyImageTools.unorganizedProductImages(product).size)
    }

    @Test
    fun locallyMissingShopifyImages() = runBlocking {
        val product = shopifyDataStore.products.first { it.title == "POLI-FLEX® PEARL GLITTER Flexfolie – A4 Bogen (20×30 cm)" }
        assertEquals(0, shopifyImageTools.locallyMissingProductImages(product).size)
    }

    @Test
    fun fixMissingSpecialCharsInMeta() = runBlocking {
        shopifyDataStore.products.forEach { product ->
            if (product.title.contains("®") && product.seoTitle?.contains("®") == false) {
                val brandName = product.title.substringBefore("®")
                product.seoTitle = product.seoTitle!!.replace(brandName, "${brandName}®")
                productClient.update(product)
            }
        }
    }

    @Test
    fun fixAltTextsWithMissingSpace() = runBlocking {
        val media = shopifyDataStore.products
            .flatMap { it.media }
            .filter { it.altText.contains(Regex("Variante:[^ ]")) }
            .onEach { it.altText = it.altText.replace("Variante:", "Variante: ") }
        media.chunked(10).forEach {
            println("Updating next chunk of ${it.size} media items")
            mediaClient.update(it)
        }
    }

    @Test
    fun findAllBarcodePrefixes() {
        artooDataStore.findAllProducts()
            .flatMap { it.variations }
            .filter { !it.barcode.isNullOrEmpty() }
            .sortedBy { it.barcode }
            .forEach { println("${it.barcode} ${it.parent.name} ${it.name}") }
    }

    @Test
    fun findProductsWithoutSeo() = runBlocking {
        shopifyDataStore.products
            .filter { it.seoTitle.isNullOrEmpty() || it.seoDescription.isNullOrEmpty() }
            .forEach { println(it.title) }
    }
}