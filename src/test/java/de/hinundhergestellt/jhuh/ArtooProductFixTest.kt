package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupType
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductType
import de.hinundhergestellt.jhuh.vendors.ready2order.client.UnsavedArtooProductGroup
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

@SpringBootTest
class ArtooProductFixTest {

    @Autowired
    private lateinit var artooProductGroupClient: ArtooProductGroupClient

    @Autowired
    private lateinit var artooProductClient: ArtooProductClient

    @Test
    @Disabled("Has run successfully")
    fun restructureProductsWithVariants() {
        val products = artooProductClient.findAll().toList()
        products.asSequence()
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

    @Test
    fun moveVariantsToProducts() {
        val products = artooProductClient.findAll().toList()
        products.asSequence()
            .filter { it.baseId == null }
            .map { it to products.filter { variant -> variant.baseId == it.id } }
            .filter { (_, variants) -> variants.isNotEmpty() }
            .forEach { (product, variants) ->
                logger.info { "Moving product ${product.name} (${variants.size} variants)" }

                val groupForProduct = artooProductGroupClient.create(
                    UnsavedArtooProductGroup(
                        product.name,
                        product.description,
                        "",
                        true,
                        product.productGroupId,
                        type = ArtooProductGroupType.VARIANTS
                    )
                )
                variants.forEach {
                    it.baseId = null
                    it.productGroupId = groupForProduct.id
                    it.type = ArtooProductType.STANDARD
                    it.alternativeNameInPos = it.name
                    it.name = "${product.name} (${it.name})"
                    it.variationsEnabled = false
                    artooProductClient.update(it)
                }
                 artooProductClient.delete(product)
            }
    }
}