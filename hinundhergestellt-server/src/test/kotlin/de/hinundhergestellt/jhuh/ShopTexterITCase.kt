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
}