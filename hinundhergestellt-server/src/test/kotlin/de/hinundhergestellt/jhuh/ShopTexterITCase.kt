package de.hinundhergestellt.jhuh

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ProductMapper
import de.hinundhergestellt.jhuh.tools.productNameForImages
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.test.Test

@SpringBootTest
class ShopTexterITCase {

    @Autowired
    private lateinit var shopTexterService: ShopTexterService

    @Autowired
    private lateinit var shopifyDataStore: ShopifyDataStore

    @Autowired
    private lateinit var productMapper: ProductMapper

    @Autowired
    private lateinit var productClient: ShopifyProductClient

    @Autowired
    private lateinit var artooDataStore: ArtooDataStore

    @Test
    fun generateCategoryTexts() {
        //generateCategoryTexts("Flexfolien", setOf("Flexfolie", "HTV", "Bügelfolie", "Textilfolie")) { "Flexfolie" in it.tags }
        //generateCategoryTexts("Bastelbedarf", setOf("Bastelbedarf")) { "Bastelbedarf" in it.tags }
        //generateCategoryTexts("Gießformen", setOf("Gießform", "Silikonform")) { "Gießform" in it.tags }
        //generateCategoryTexts("Wolle", setOf("Wolle")) { "Wolle" in it.tags }
        //generateCategoryTexts("Häkeln und Stricken", setOf("Häkeln", "Stricken", "Wolle", "Füllwatte", "Häkelnadel", "Nadelspiel")) {
        //    "Häkeln" in it.tags || "Stricken" in it.tags
        //}
        //generateCategoryTexts("Bastelsets", setOf("Bastelset")) { "Bastelset" in it.tags }
        //generateCategoryTexts("Flockfolien", setOf("Flockfolie", "HTV", "Bügelfolie", "Textilfolie")) { "Flockfolie" in it.tags }
        //generateCategoryTexts("Plotten", setOf("Plotten", "Flexfolie", "Flockfolie", "HTV", "Bügelfolie", "Textilfolie",
        //    "Vinylfolie", "Klebefolie", "Möbelfolie")) { "Plotten" in it.tags }
        //generateCategoryTexts("Transferfolien", setOf("Transferfolie")) { "Transferfolie" in it.tags }
//        val allTags = shopifyDataStore.products.asSequence().flatMap { it.tags }.toSet()
//        generateCategoryTexts("Alle Produkte", allTags) { true }

        generateCategoryTexts("Quilts & Taschen", setOf("Quilt", "Tasche", "Handarbeit", "Einzelstück")) {
            "Quilt" in it.tags || "Tasche" in it.tags
        }
    }

    private fun generateCategoryTexts(category: String, tags: Set<String>, filter: (ShopifyProduct) -> Boolean) {
        val products = shopifyDataStore.products.filter(filter)
        val category = shopTexterService.generateCategoryTexts(category, tags, products)

        println()
        println("SEO-Titel:")
        println(category.seoTitle)
        println()
        println("Meta-Beschreibung:")
        println(category.metaDescription)
        println()
        println("HTML-Beschreibung:")
        println(category.descriptionHtml)
    }

    @Test
    fun generateProductTexts() {
        val product = shopifyDataStore.products.first { it.title.contains("handmade with love") }
        val generated = shopTexterService.generateProductTexts(product)

        println()
        println("SEO-Titel:")
        println(generated.seoTitle)
        println()
        println("Meta-Beschreibung:")
        println(generated.metaDescription)
        println()
        println("HTML-Beschreibung:")
        println(generated.descriptionHtml)
    }

    @Test
    fun generateProductDetails() {
        val product = shopifyDataStore.products.first { it.title.contains("handmade with love") }
        val generated = shopTexterService.generateProductDetails(product)

        println()
        println("Produktart: ${generated.productType}")
        println("Tags: ${generated.tags}")
        println()
        println("Technische Daten:")
        println(generated.technicalDetails)
    }

    @Test
    fun exportProducts() {
        val products = shopifyDataStore.products
            .filter { it.tags.contains("Flexfolie") }
            .map { productMapper.map(it) }
        val json = jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .writeValueAsString(products)
        Files.write(Path("/home/lordjaxom/Downloads/products.json"), json.toByteArray(StandardCharsets.UTF_8))
    }

    @Test
    fun exportCollections() = runBlocking {
        val collections = productClient.findCollections()
            .map { Category(it.handle, it.title, it.descriptionHtml, it.seo.title, it.seo.description) }
            .toList()
        val json = jacksonObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .writeValueAsString(collections)
        Files.write(Path("/home/lordjaxom/Downloads/collections.json"), json.toByteArray(StandardCharsets.UTF_8))
        Unit
    }

    @Test
    fun reworkProductTexts() {
        shopifyDataStore.products
            .filter { it.seoTitle.isNullOrEmpty() }
            .forEach { reworkProductTexts(it) }
    }

    private fun reworkProductTexts(product: ShopifyProduct) = runBlocking {
        val reworked = shopTexterService.reworkProductTexts(product)

        val handle = reworked.handle
        if (handle.isNotEmpty() && handle != product.handle) {
            println("Handle: ${product.handle} to $handle")
            product.handle = handle
        }
        val title = reworked.title
        if (title.isNotEmpty() && title != product.title) {
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

            val artoo =
                artooDataStore.findAllProducts().first { it.barcodes.any { barcode -> product.findVariantByBarcode(barcode) != null } }
            println("Updating Artoo product name: ${artoo.description} to $title")
            artoo.description = title
            artooDataStore.update(artoo)

            println("Title: ${product.title} to $title")
            product.title = title
        }
        val seoTitle = reworked.seoTitle
        if (seoTitle.isNotEmpty() && seoTitle != product.seoTitle) {
            println("SEO Title: ${product.seoTitle} to $seoTitle")
            product.seoTitle = seoTitle
        }
        val seoDescription = reworked.seoDescription
        if (seoDescription.isNotEmpty() && seoDescription != product.seoDescription) {
            println("SEO Description: ${product.seoDescription} to $seoDescription")
            product.seoDescription = seoDescription
        }

        val descriptionHtml = reworked.descriptionHtml
        if (descriptionHtml.isNotEmpty() && descriptionHtml != product.descriptionHtml) {
            product.descriptionHtml = descriptionHtml
        }

        productClient.update(product)
    }
}

class Category(
    val handle: String,
    val title: String,
    val descriptionHtml: String,
    val seoTitle: String?,
    val seoDescription: String?
)
