package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldsClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest
@Disabled("Only run manually")
class ShopifyProductsFixITCase {

    @Autowired
    private lateinit var productClient: ShopifyProductClient

    @Autowired
    private lateinit var optionClient: ShopifyProductOptionClient

    @Autowired
    private lateinit var variantClient: ShopifyProductVariantClient

    @Autowired
    private lateinit var metafieldsClient: ShopifyMetafieldsClient

    @Test
    fun findAllProducts(): Unit = runBlocking {
        productClient.fetchAll().toList()
    }

    @Test
    fun associateMedia(): Unit = runBlocking {
        val product = productClient.fetchAll().first { it.title.startsWith("craftcut® glänzend") }
        val changedVariants = product.variants.asSequence()
            .filter { it.options.isNotEmpty() && it.mediaId == null }
            .map {
                it to product.media.filter { image ->
                    image.src.contains("""\b${Regex.fromLiteral(it.sku)}\b""".toRegex(RegexOption.IGNORE_CASE))
                }
            }
            .filter { (_, images) -> images.size == 1 }
            .onEach { (variant, images) -> variant.mediaId = images[0].id }
            .map { (variant, _) -> variant }
            .toList()
        variantClient.update(product, changedVariants)
    }

    @Test
    fun findShopifyProductId() = runBlocking {
        println(productClient.fetchAll().first { it.title.startsWith("SUPERIOR® Matt Chrome") }.id)
    }
}