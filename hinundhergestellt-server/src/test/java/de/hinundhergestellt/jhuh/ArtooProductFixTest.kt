package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupType
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductType
import de.hinundhergestellt.jhuh.vendors.ready2order.client.UnsavedArtooProductGroup
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

@SpringBootTest
@Disabled("Only run manually")
class ArtooProductFixTest {

    @Autowired
    private lateinit var artooProductGroupClient: ArtooProductGroupClient

    @Autowired
    private lateinit var artooProductClient: ArtooProductClient

    @Test
    @Disabled("Has run successfully")
    fun restructureProductsWithVariants() = runBlocking {
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
    @Disabled("Has run successfully")
    fun moveVariantsToProducts() = runBlocking {
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

    @Test
    @Disabled("Has run successfully")
    fun updateProductsWithVariantsName() = runBlocking {
        val oldName = "Gründl - Funny Mini 15g"
        val newName = "Gründl Funny Mini"

        val groups = artooProductGroupClient.findAll().toList()
        val products = artooProductClient.findAll().toList()

        val groupToUpdate = groups.find { it.name == oldName }!!
        groupToUpdate.name = newName
        groupToUpdate.description = groupToUpdate.description.replace(oldName, newName)
        artooProductGroupClient.update(groupToUpdate)

        products.asSequence()
            .filter { it.productGroupId == groupToUpdate.id }
            .forEach { product ->
                product.name = product.name.replace(oldName, newName)
                artooProductClient.update(product)
            }
    }
}