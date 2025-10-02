package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class ShopifyCategoryTaxonomyProviderTest {

    @Test
    fun loadAnyCategory() {
        val category = ShopifyCategoryTaxonomyProvider.categories["ae-2-1-2-6-4"]!!
        assertEquals("ae-2-1-2-6-4", category.id)
        assertEquals("Garn", category.name)
    }
}