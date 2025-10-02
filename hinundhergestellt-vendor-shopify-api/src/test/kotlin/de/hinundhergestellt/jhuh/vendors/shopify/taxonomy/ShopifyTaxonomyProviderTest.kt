package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class ShopifyTaxonomyProviderTest {

    @Test
    fun `test download taxonomy from Github`() {
        assertThat(ShopifyTaxonomyProvider.taxonomy.first { it.key == 7 })
            .extracting(RawTaxonomy::name, RawTaxonomy::handle)
            .containsExactly("Brown", "color__brown")
    }
}