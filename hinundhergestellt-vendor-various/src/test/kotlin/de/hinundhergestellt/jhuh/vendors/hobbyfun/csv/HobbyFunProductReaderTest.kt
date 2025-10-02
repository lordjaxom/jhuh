package de.hinundhergestellt.jhuh.vendors.hobbyfun.csv

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class HobbyFunProductReaderTest {

    @Test
    fun `reads HobbyFun products correctly`() {
        val products = javaClass.getResourceAsStream("/hobbyfun_products.csv")!!.use { readHobbyFunProducts(it) }
        assertThat(products).hasSize(2)
        assertThat(products[0]).isEqualTo(
            HobbyFunProduct(
                articleNumber = "1000 025",
                description = "Keraflott Gießmasse 25kg Sack, weiß",
                ean = "4036159100250",
                imageUrl = "http://ftp.hobbyfun.de/pdb/1000025.jpg"
            )
        )
        assertThat(products[1]).isEqualTo(
            HobbyFunProduct(
                articleNumber = "3453 501",
                description = "Sticker \"Thank you\"",
                ean = "4036159533669",
                imageUrl = "http://ftp.hobbyfun.de/pdb/3453501.jpg"
            )
        )
    }
}