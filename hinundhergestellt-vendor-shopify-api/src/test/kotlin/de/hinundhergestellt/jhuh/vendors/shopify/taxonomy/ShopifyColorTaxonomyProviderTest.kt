package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.awt.Color
import kotlin.test.Test

@SpringBootTest
class ShopifyColorTaxonomyProviderTest {

    @Autowired
    private lateinit var providerUnderTest: ShopifyColorTaxonomyProvider

    @Test
    fun `test download taxonomy from Github`() {
        assertThat(providerUnderTest.values.first { it.name == "brown" })
            .extracting(ShopifyColorTaxonomy::id, ShopifyColorTaxonomy::name, ShopifyColorTaxonomy::color)
            .containsExactly(7, "brown", Color(165, 42, 42))
    }
}