package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.backend.shoptexter.ShopTexterService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest
class ShopTexterITCase {

    @Autowired
    private lateinit var shopTexterService: ShopTexterService

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
        val category = "Bastelbedarf"
        val tags = setOf("Bastelbedarf")
        val allOf = true
        val keywords = shopTexterService.generateCategoryKeywords(category, tags, allOf)
        val texts = shopTexterService.generateCategoryTexts(category, tags, keywords)
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