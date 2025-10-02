package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import org.assertj.core.api.Assertions.assertThat
import java.awt.Color
import kotlin.test.Test

class ShopifyColorTaxonomyProviderTest {

    @Test
    fun `test downloades taxonomy`() {
        assertThat(ShopifyColorTaxonomyProvider.colors.values.first { it.name == "brown" })
            .extracting(ShopifyColorTaxonomy::id, ShopifyColorTaxonomy::name, ShopifyColorTaxonomy::color)
            .containsExactly("gid://shopify/TaxonomyValue/7", "brown", Color(165, 42, 42))
    }
}