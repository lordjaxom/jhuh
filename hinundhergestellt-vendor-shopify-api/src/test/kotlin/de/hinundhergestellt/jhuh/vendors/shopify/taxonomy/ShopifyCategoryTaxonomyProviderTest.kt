package de.hinundhergestellt.jhuh.vendors.shopify.taxonomy

import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class ShopifyCategoryTaxonomyProviderTest {

    @Test
    fun loadAnyCategory() {
        val category = ShopifyCategoryTaxonomyProvider.categories["gid://shopify/TaxonomyCategory/ae-2-1-2-6-4"]!!
        assertEquals("gid://shopify/TaxonomyCategory/ae-2-1-2-6-4", category.id)
        assertEquals("Garn", category.name)
    }
}