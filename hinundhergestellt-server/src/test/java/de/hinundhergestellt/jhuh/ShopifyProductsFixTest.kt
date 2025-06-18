package de.hinundhergestellt.jhuh

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

}