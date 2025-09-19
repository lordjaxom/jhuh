package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
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
        val category = "Flexfolien"
        val tags = setOf("Flexfolie", "HTV", "Bügelfolie", "Textilfolie")
        val products = shopifyDataStore.products.filter { "Flexfolie" in it.tags }
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