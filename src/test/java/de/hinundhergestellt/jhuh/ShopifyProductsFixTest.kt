package de.hinundhergestellt.jhuh

import arrow.core.raise.option
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ShopifyProductsFixTest {

    @Autowired
    private lateinit var productClient: ShopifyProductClient
    @Autowired
    private lateinit var optionClient: ShopifyProductOptionClient
    @Autowired
    private lateinit var variantClient: ShopifyProductVariantClient

    @Test
    fun makeVariantDefault() {
        var product = productClient.findAll().find { it.title.contains("Übertragungsfolie") }!!
        println(product.hasOnlyDefaultVariant)
        optionClient.delete(product, product.options.subList(0, 1))
        product = productClient.findAll().find { it.title.contains("Übertragungsfolie") }!!
        println(product.hasOnlyDefaultVariant)
    }
}