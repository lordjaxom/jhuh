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
    fun generateCategoryDescription() {
        val result = shopTexterService.generateCategoryDescription(
            "Plotten",
            setOf("Plotten", "Plotterfolie", "Flexfolie", "Transferfolie", "Vinylfolie", "Flockfolie", "Zubehör")
        )
        println(result.description)
    }

    @Test
    fun generateCategoryTexts() {
        //generateCategoryTexts("Flexfolien", setOf("Flexfolie", "HTV", "Bügelfolie", "Textilfolie")) { "Flexfolie" in it.tags }
        //generateCategoryTexts("Bastelbedarf", setOf("Bastelbedarf")) { "Bastelbedarf" in it.tags }
        //generateCategoryTexts("Gießformen", setOf("Gießform", "Silikonform")) { "Gießform" in it.tags }
        //generateCategoryTexts("Wolle", setOf("Wolle")) { "Wolle" in it.tags }
        //generateCategoryTexts("Häkeln und Stricken", setOf("Häkeln", "Stricken", "Wolle", "Füllwatte", "Häkelnadel", "Nadelspiel")) {
        //    "Häkeln" in it.tags || "Stricken" in it.tags
        //}
        generateCategoryTexts("Bastelsets", setOf("Bastelset")) { "Bastelset" in it.tags }
    }

    private fun generateCategoryTexts(category: String, tags: Set<String>, filter: (ShopifyProduct) -> Boolean) {
        val products = shopifyDataStore.products.filter(filter)
        val keywords = shopTexterService.generateCategoryKeywords(category, tags, products)
        val texts = shopTexterService.generateCategoryTexts(category, tags, products, keywords)
        val optimized = shopTexterService.optimizeCategoryTexts(category, texts)

        println()
        println("SEO-Titel:")
        println(texts.seoTitle)
        println()
        println("Meta-Beschreibung:")
        println(texts.metaDescription)
        println()
        println("HTML-Beschreibung:")
        println(optimized.description)
    }
}