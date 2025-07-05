package de.hinundhergestellt.jhuh.vendors.rayher.csv

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class RayherProductReaderTest {

    @Test
    fun `reads Rayher products correctly`() {
        val products = javaClass.getResourceAsStream("/rayher_products.csv")!!.use { readRayherProducts(it) }
        assertThat(products).hasSize(2)
        assertThat(products[0]).isEqualTo(
            RayherProduct(
                supplierId = "4006166",
                articleNumber = "1201031",
                description = "Holzperlen, poliert, 30 mm ø, natur, 10 mm Loch, lose",
                descriptions = listOf("Holzperlen, poliert, 30 mm ø", "10 mm Loch, lose", "natur"),
                ean = "4006166011726",
                imageUrls = listOf("https://pics.rayher.com/1201031-01_PF.jpg", "https://pics.rayher.com/1201031_PF.jpg")
            )
        )
        assertThat(products[1]).isEqualTo(
            RayherProduct(
                supplierId = "4006166",
                articleNumber = "15406000",
                description = "Perlen Bastel Dose \"Lovely\", 10,7cm ø, versch. Farben+Größen, in Blüten Box",
                ean = "4006166937583",
                descriptions = listOf("Perlen Bastel Dose \"Lovely\", 10,7cm ø", "versch. Farben+Größen, in Blüten Box"),
                imageUrls = listOf("https://pics.rayher.com/15406000_PF.jpg")
            )
        )
    }
}