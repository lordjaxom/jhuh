package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOption
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShopifyProductOptionTest {

    @Test
    fun `test creation from ProductOption`() {
        val mockOption = mockk<ProductOption>()
        every { mockOption.id } returns "OPTID1"
        every { mockOption.name } returns "Farbe"
        every { mockOption.values } returns listOf("Rot", "Blau")
        val option = ShopifyProductOption(mockOption)
        assertThat(option.id).isEqualTo("OPTID1")
        assertThat(option.name).isEqualTo("Farbe")
        assertThat(option.values).containsExactly("Rot", "Blau")
    }

    @Test
    fun `test creation from UnsavedShopifyProductOption and id`() {
        val unsaved = UnsavedShopifyProductOption("Größe", listOf("S", "M"))
        val option = ShopifyProductOption(unsaved, "OPTID2")
        assertThat(option.id).isEqualTo("OPTID2")
        assertThat(option.name).isEqualTo("Größe")
        assertThat(option.values).containsExactly("S", "M")
    }

    @Test
    fun `test value constructor`() {
        val option = ShopifyProductOption("OPTID3", "Material", listOf("Wolle", "Baumwolle"))
        assertThat(option.id).isEqualTo("OPTID3")
        assertThat(option.name).isEqualTo("Material")
        assertThat(option.values).containsExactly("Wolle", "Baumwolle")
    }

    @Test
    fun `test toString output`() {
        val option = ShopifyProductOption("IDSTR", "Farbe", listOf("Rot", "Blau"))
        val expected = "ShopifyProductOption(id='IDSTR', name='Farbe')"
        assertThat(option.toString()).isEqualTo(expected)
    }

    @Test
    fun `test dirty tracking`() {
        val option = ShopifyProductOption("OPTID4", "Material", listOf("Wolle", "Baumwolle"))
        // initially not dirty
        assertThat(option.dirtyTracker.getDirtyAndReset()).isFalse()
        // change name
        option.name = "Neues Material"
        assertThat(option.dirtyTracker.getDirtyAndReset()).isTrue()
        assertThat(option.dirtyTracker.getDirtyAndReset()).isFalse()
        // change to same value should not mark dirty
        option.name = "Neues Material"
        assertThat(option.dirtyTracker.getDirtyAndReset()).isFalse()
    }
}
