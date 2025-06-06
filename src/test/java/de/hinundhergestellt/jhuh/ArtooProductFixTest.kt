package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest
class ArtooProductFixTest {

    @Autowired
    private lateinit var artooProductClient: ArtooProductClient

    @Test
    fun restructureProductsWithVariants() {
        val products = artooProductClient.findAll().toList()
        products.asSequence()
            .filter { it.name == "Testprodukt" }
            .filter { it.baseId == null }
            .filter { it.price.compareTo(BigDecimal.ZERO) != 0 }
            .map { it to products.filter { variant -> variant.baseId == it.id } }
            .filter { (_, variants) -> variants.isNotEmpty() }
            .forEach { (product, variants) ->
                variants.forEach {
                    it.price += product.price
                    artooProductClient.update(it)
                }
                product.price = BigDecimal.ZERO
                artooProductClient.update(product)
            }
    }
}