package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest
class ShopTexterITCase {

    @Autowired
    private lateinit var shopTexterService: ShopTexterService

    @Autowired
    private lateinit var shopifyDataStore: ShopifyDataStore

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
        generateCategoryTexts("Alle Produkte", setOf()) { true }
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
    fun generateProductTexts(){
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
}