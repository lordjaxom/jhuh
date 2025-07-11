package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.SelectedOption
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.VariantOptionValueInput
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShopifyProductVariantOptionTest {

    @Test
    fun `test creation from primary constructor`() {
        val option = ShopifyProductVariantOption("Farbe", "Rot")
        assertThat(option.name).isEqualTo("Farbe")
        assertThat(option.value).isEqualTo("Rot")
    }

    @Test
    fun `test creation from SelectedOption`() {
        val selected = mockk<SelectedOption> {
            every { name } returns "Material"
            every { value } returns "Wolle"
        }
        val option = ShopifyProductVariantOption(selected)
        assertThat(option.name).isEqualTo("Material")
        assertThat(option.value).isEqualTo("Wolle")
    }

    @Test
    fun `test toString output`() {
        val option = ShopifyProductVariantOption("Größe", "L")
        val expected = "ShopifyProductVariantOption(name='Größe', value='L')"
        assertThat(option.toString()).isEqualTo(expected)
    }

    @Test
    fun `test toVariantOptionValueInput`() {
        val option = ShopifyProductVariantOption("Finish", "Matt")
        val input = option.toVariantOptionValueInput()
        assertThat(input).isInstanceOf(VariantOptionValueInput::class.java)
        assertThat(input.optionName).isEqualTo("Finish")
        assertThat(input.name).isEqualTo("Matt")
    }

    @Test
    fun `test dirty tracking`() {
        val option = ShopifyProductVariantOption("Farbe", "Blau")
        // initially not dirty
        assertThat(option.dirtyTracker.getDirtyAndReset()).isFalse()
        // after changing value, it should be dirty
        option.value = "Grün"
        assertThat(option.dirtyTracker.getDirtyAndReset()).isTrue()
        assertThat(option.dirtyTracker.getDirtyAndReset()).isFalse()
    }
}

