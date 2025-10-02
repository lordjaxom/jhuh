package de.hinundhergestellt.jhuh.vendors.google.taxonomy

import org.junit.jupiter.api.Assertions
import kotlin.test.Test

class GoogleCategoryTaxonomyProviderTest {

    @Test
    fun `loads any category`() {
        val category = GoogleCategoryTaxonomyProvider.categories[4486]!!
        Assertions.assertEquals(4486, category.id)
        Assertions.assertEquals("Zubehör für Baby- & Kleinkindautositze", category.name)
        Assertions.assertEquals("Baby & Kleinkind > Babytransportzubehör > Zubehör für Baby- & Kleinkindautositze", category.context)
    }
}