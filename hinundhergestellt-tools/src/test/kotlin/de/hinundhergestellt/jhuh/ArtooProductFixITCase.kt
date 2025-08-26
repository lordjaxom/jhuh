package de.hinundhergestellt.jhuh

import arrow.core.zip
import de.hinundhergestellt.jhuh.core.ifDirty
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroup
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupClient
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroupType
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductType
import de.hinundhergestellt.jhuh.vendors.ready2order.client.UnsavedArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.client.UnsavedArtooProductGroup
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.math.BigDecimal
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

@SpringBootTest
@Disabled("Only run manually")
class ArtooProductFixITCase {

    @Autowired
    private lateinit var artooProductGroupClient: ArtooProductGroupClient

    @Autowired
    private lateinit var artooProductClient: ArtooProductClient

    @Test
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
    fun updateProductsWithVariantsName() = runBlocking {
        val oldName = "SUPERIOR Holo-Brushed Vinylfolie"
        val newName = "SUPERIOR® Holo-Brushed"

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

    @Test
    fun setStockReorderLevel() = runBlocking {
        val groups = artooProductGroupClient.findAll().toList()
        val vinylfolien = groups.find { it.name == "Vinylfolien" }!!
        val flexfolien = groups.find { it.name == "Flexfolien" }!!
        val flockfolien = groups.find { it.name == "Flockfolien" }!!
        artooProductClient.findAll()
            .filter {
                it.productGroupIdChain(groups).toList()
                    .run { contains(vinylfolien.id) || contains(flexfolien.id) || contains(flockfolien.id) }
            }
            .collect { product ->
                product.stockReorderLevel = BigDecimal(3)
                product.ifDirty { artooProductClient.update(it) }
            }
    }

    @Test
    fun addNewMyboshiProducts(): Unit = runBlocking {
        val group = artooProductGroupClient.findAll().filter { it.name == "myboshi" }.single()

        val groupForProduct = artooProductGroupClient.findAll().filter { it.name == "myboshi Dream" }.single()
//            artooProductGroupClient.create(
//            UnsavedArtooProductGroup(
//                "myboshi Dream",
//                "myboshi Dream, 50g, 76% Baumwolle, 22% Baby-Alpaka, 2% Merino",
//                "",
//                true,
//                group.id,
//                type = ArtooProductGroupType.VARIANTS
//            )
//        )

        val skus = """
            WDR001
            WDR002
            WDR003
            WDR004
            WDR005
            WDR006
        """.trimIndent().split("\n")
        val names = """
            Himmelblau
            Bergsee
            Ozean
            Morgentau
            Sanddüne
            Wolke
        """.trimIndent().split("\n")
        val barcodes = """
            4251260517869
            4251260517876
            4251260517883
            4251260517890
            4251260517906
            4251260517913
        """.trimIndent().split("\n")
        val price = BigDecimal("6.99")

        names.zip(barcodes, skus) { name, barcode, sku ->
            try {
                artooProductClient.create(
                    UnsavedArtooProduct(
                        name = "myboshi Dream ($name)",
                        itemNumber = sku,
                        barcode = barcode,
                        description = "",
                        price = price,
                        priceIncludesVat = true,
                        vat = BigDecimal("19"),
                        stockEnabled = true,
                        stockValue = BigDecimal("10"),
                        active = true,
                        productGroupId = groupForProduct.id,
                        type = ArtooProductType.STANDARD,
                        alternativeNameInPos = name
                    )
                )
            } catch (e: WebClientResponseException) {
                println(e.getResponseBodyAs(String::class.java))
                throw e
            }
        }
    }

    @Test
    fun fixGroupOfRayerSilikonformen() = runBlocking{
        val group = artooProductGroupClient.findAll().first { it.name == "Rayher Silikonformen" }
        artooProductClient.findAll()
            .filter { it.name.contains("Silikon Gießform") && it.productGroupId != group.id }
            .collect { product ->
                logger.info { "Updating group of ${product.name}"}
                product.productGroupId = group.id
                artooProductClient.update(product)
            }
    }
}

private fun ArtooProduct.productGroupIdChain(groups: List<ArtooProductGroup>): Sequence<Int> =
    sequence {
        groups.find { it.id == productGroupId }!!.also {
            yield(it.id)
            yieldAll(it.productGroupIdChain(groups))
        }
    }

private fun ArtooProductGroup.productGroupIdChain(groups: List<ArtooProductGroup>): Sequence<Int> =
    sequence {
         groups.find { it.id == parent }?.also {
             yield(it.id)
             yieldAll(it.productGroupIdChain(groups))
         }
    }
