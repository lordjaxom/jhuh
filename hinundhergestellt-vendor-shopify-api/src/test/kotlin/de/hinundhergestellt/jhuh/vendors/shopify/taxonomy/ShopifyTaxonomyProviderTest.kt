package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest
class ShopifyTaxonomyProviderTest {

    @Autowired
    private lateinit var providerUnderTest: ShopifyTaxonomyProvider

    @Test
    fun `test download taxonomy from Github`() {
        assertThat(providerUnderTest.values.first { it.id == 7 })
            .extracting(ShopifyTaxonomyValue::name, ShopifyTaxonomyValue::handle)
            .containsExactly("Brown", "color__brown")
    }
}