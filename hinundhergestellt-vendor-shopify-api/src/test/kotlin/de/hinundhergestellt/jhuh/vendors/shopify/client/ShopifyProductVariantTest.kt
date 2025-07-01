
package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaEdge
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.SelectedOption
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ShopifyProductVariantTest {

    @Test
    fun `test creation from Unsaved`() {
        val unsaved = UnsavedShopifyProductVariant(
            sku = "SKU123",
            barcode = "BARCODE123",
            price = BigDecimal("12.34"),
            weight = Weight(WeightUnit.KILOGRAMS, 1.0),
            options = listOf(ShopifyProductVariantOption("Color", "Red")),
            inventoryLocationId = "LOC123",
            inventoryQuantity = 10
        )

        val variant = ShopifyProductVariant(unsaved, "ID123", "My Title")
        assertThat(variant.id).isEqualTo("ID123")
        assertThat(variant.title).isEqualTo("My Title")
        assertThat(variant.sku).isEqualTo("SKU123")
        assertThat(variant.price).isEqualByComparingTo("12.34")
        assertThat(variant.weight.unit).isEqualTo(WeightUnit.KILOGRAMS)
        assertThat(variant.weight.value).isEqualTo(1.0)
        assertThat(variant.options).hasSize(1) // TODO
    }

    @Test
    fun `test creation from ProductVariant`() {
        val mockVariant = mockk<ProductVariant>()
        every { mockVariant.sku } returns "SKU567"
        every { mockVariant.barcode } returns "BAR567"
        every { mockVariant.price } returns "29.99"
        every { mockVariant.inventoryItem.measurement.weight } returns Weight(WeightUnit.KILOGRAMS, 2.0)
        every { mockVariant.selectedOptions } returns listOf(
            mockk<SelectedOption>().apply {
                every { name } returns "Size"
                every { value } returns "XL"
            }
        )
        every { mockVariant.id } returns "ID567"
        every { mockVariant.title } returns "Variant XL"
        every { mockVariant.media.edges } returns listOf(
            mockk<MediaEdge>().apply {
                every { node.id } returns "MEDIA567"
            }
        )
        every { mockVariant.media.pageInfo.hasNextPage } returns false

        val variant = ShopifyProductVariant(mockVariant)

        assertThat(variant.id).isEqualTo("ID567")
        assertThat(variant.title).isEqualTo("Variant XL")
        assertThat(variant.sku).isEqualTo("SKU567")
        assertThat(variant.price).isEqualByComparingTo("29.99")
        assertThat(variant.weight.unit).isEqualTo(WeightUnit.KILOGRAMS)
        assertThat(variant.weight.value).isEqualTo(2.0)
        assertThat(variant.options).hasSize(1)
    }

    @Test
    fun `test toProductVariantsBulkInput`() {
        val unsaved = UnsavedShopifyProductVariant(
            sku = "SKU444",
            barcode = "BAR444",
            price = BigDecimal("25.00"),
            weight = Weight(WeightUnit.KILOGRAMS, 1.0),
            options = listOf(ShopifyProductVariantOption("Finish", "Glossy")),
            inventoryLocationId = "LOC444",
            inventoryQuantity = 50
        )

        val variant = ShopifyProductVariant(unsaved, "ID444", "Glossy Variant")
        variant.mediaId = "MEDIA444"
        val input = variant.toProductVariantsBulkInput()

        assertThat(input.id).isEqualTo("ID444")
        assertThat(input.barcode).isEqualTo("BAR444")
        assertThat(input.price).isEqualTo("25.00")
        assertThat(input.optionValues).hasSize(1)
        assertThat(input.inventoryItem?.sku).isEqualTo("SKU444")
        assertThat(input.inventoryItem?.tracked).isTrue()
        assertThat(input.inventoryItem?.measurement?.weight?.unit).isEqualTo(WeightUnit.KILOGRAMS)
        assertThat(input.inventoryItem?.measurement?.weight?.value).isEqualTo(1.0)
        assertThat(input.inventoryQuantities).isNull()
        assertThat(input.mediaId).isEqualTo("MEDIA444")
    }

    @Test
    fun `test toString returns full information`() {
        val unsaved = UnsavedShopifyProductVariant(
            sku = "SKU999",
            barcode = "BAR999",
            price = BigDecimal("9.99"),
            weight = Weight(WeightUnit.KILOGRAMS, 2.5),
            options = listOf(ShopifyProductVariantOption("Size", "L")),
            inventoryLocationId = "LOC001",
            inventoryQuantity = 5
        )

        val variant = ShopifyProductVariant(unsaved, "ID999", "Variant Title")
        val expected = "ShopifyProductVariant(id='ID999', title='Variant Title', sku='SKU999', barcode='BAR999', price=9.99)"
        assertThat(variant.toString()).isEqualTo(expected)
    }

    @Test
    fun `test validation fails if multiple media present`() {
        val mockVariant = mockk<ProductVariant>()
        every { mockVariant.sku } returns "SKU"
        every { mockVariant.barcode } returns "BARCODE"
        every { mockVariant.price } returns "11.00"
        every { mockVariant.inventoryItem.measurement.weight } returns Weight(WeightUnit.KILOGRAMS, 1.0)
        every { mockVariant.selectedOptions } returns listOf(
            mockk<SelectedOption>().apply {
                every { name } returns "Color"
                every { value } returns "Green"
            }
        )
        every { mockVariant.id } returns "IDX"
        every { mockVariant.title } returns "Variant X"
        every { mockVariant.media.edges } returns listOf(
            mockk<MediaEdge>().apply {
                every { node.id } returns "MEDIA1"
            }
        )
        every { mockVariant.media.pageInfo.hasNextPage } returns true

        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            ShopifyProductVariant(mockVariant)
        }
    }
}
