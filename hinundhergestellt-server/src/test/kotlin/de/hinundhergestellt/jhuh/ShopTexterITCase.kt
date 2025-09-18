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
        val keywords = shopTexterService.generateCategoryKeywords("Flexfolien", setOf("Flexfolie"))
        val texts = shopTexterService.generateCategoryTexts("Flexfolien", setOf("Flexfolie"), keywords)
        val optimized = shopTexterService.optimizeCategoryTexts("Flexfolien", texts)
        println(optimized)
    }
}