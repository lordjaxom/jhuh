package de.hinundhergestellt.jhuh

import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldType
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafieldsClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionClient
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariantClient
import org.assertj.core.api.Assertions.assertThat
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
    @Autowired
    private lateinit var metafieldsClient: ShopifyMetafieldsClient

    @Test
    fun addMetaDataToProduct() {
        var product = productClient.findAll().find { it.title.startsWith("myboshi Samt XL") }!!
        assertThat(product.metafields).isEmpty()
        product.metafields.add(ShopifyMetafield("hinundhergestellt", "vendor-address", "xxx", ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD))
        productClient.update(product)
        product = productClient.findAll().find { it.title.startsWith("myboshi Samt XL") }!!
        assertThat(product.metafields).isNotEmpty()
        metafieldsClient.delete(product, product.metafields)
        product = productClient.findAll().find { it.title.startsWith("myboshi Samt XL") }!!
        assertThat(product.metafields).isEmpty()
    }
}