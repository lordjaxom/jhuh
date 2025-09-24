package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaEdge
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.SelectedOption
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Weight
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.apply

class ShopifyProductVariantTest {

    @Test
    fun `test creation from Unsaved`() {
        val option = ShopifyProductOptionValue("Color", "Red")
        val unsaved = UnsavedShopifyProductVariant(
            sku = "SKU123",
            barcode = "BARCODE123",
            price = BigDecimal("12.34"),
            weight = ShopifyWeight(WeightUnit.KILOGRAMS, BigDecimal("1.0")),
            options = listOf(option),
            inventoryLocationId = "LOC123",
            inventoryQuantity = 10
        )

        val variant = ShopifyProductVariant(unsaved, "ID123", "My Title")
        assertThat(variant.id).isEqualTo("ID123")
        assertThat(variant.title).isEqualTo("My Title")
        assertThat(variant.sku).isEqualTo("SKU123")
        assertThat(variant.price).isEqualByComparingTo("12.34")
        assertThat(variant.weight.unit).isEqualTo(WeightUnit.KILOGRAMS)
        assertThat(variant.weight.value).isEqualTo(BigDecimal("1.00"))
        assertThat(variant.options).containsExactly(option)
    }
//
//    @Test
//    fun `test creation from ProductVariant`() {
//        val mockVariant = mockk<ProductVariant>()
//        every { mockVariant.sku } returns "SKU567"
//        every { mockVariant.barcode } returns "BAR567"
//        every { mockVariant.price } returns "29.99"
//        every { mockVariant.inventoryItem.measurement.weight } returns
//                Weight.Builder().withUnit(WeightUnit.KILOGRAMS).withValue(BigDecimal("2.0")).build()
//        every { mockVariant.selectedOptions } returns listOf(
//            mockk<SelectedOption>().apply {
//                every { name } returns "Size"
//                every { value } returns "XL"
//                every { optionValue } returns ProductOptionValue("OPT1", "LMV1")
//            }
//        )
//        every { mockVariant.id } returns "ID567"
//        every { mockVariant.title } returns "Variant XL"
//        every { mockVariant.media.edges } returns listOf(
//            mockk<MediaEdge>().apply {
//                every { node.id } returns "MEDIA567"
//            }
//        )
//        every { mockVariant.media.pageInfo.hasNextPage } returns false
//
//        val variant = ShopifyProductVariant(mockVariant)
//
//        assertThat(variant.id).isEqualTo("ID567")
//        assertThat(variant.title).isEqualTo("Variant XL")
//        assertThat(variant.sku).isEqualTo("SKU567")
//        assertThat(variant.price).isEqualByComparingTo("29.99")
//        assertThat(variant.weight.unit).isEqualTo(WeightUnit.KILOGRAMS)
//        assertThat(variant.weight.value).isEqualTo(BigDecimal("2.00"))
//        assertThat(variant.options).hasSize(1)
//    }

    @Test
    fun `test toProductVariantsBulkInput`() {
        val option = ShopifyProductOptionValue("Finish", "Glossy")
        val variant = ShopifyProductVariant(
            id = "ID444",
            title = "Glossy Variant",
            sku = "SKU444",
            barcode = "BAR444",
            price = BigDecimal("25.00"),
            weight = ShopifyWeight(WeightUnit.KILOGRAMS, BigDecimal("1.0")),
            options = listOf(option),
            mediaId = "MEDIA444"
        )
        val input = variant.toProductVariantsBulkInput()
        assertThat(input.id).isEqualTo("ID444")
        assertThat(input.barcode).isEqualTo("BAR444")
        assertThat(input.price).isEqualTo("25.00")
        assertThat(input.optionValues).containsExactly(option.toVariantOptionValueInput())
        assertThat(input.inventoryItem?.sku).isEqualTo("SKU444")
        assertThat(input.inventoryItem?.tracked).isTrue()
        assertThat(input.inventoryItem?.measurement?.weight?.unit).isEqualTo(WeightUnit.KILOGRAMS)
        assertThat(input.inventoryItem?.measurement?.weight?.value).isEqualTo(BigDecimal("1.00"))
        assertThat(input.inventoryQuantities).isNull()
        assertThat(input.mediaId).isEqualTo("MEDIA444")
    }

    @Test
    fun `test toString returns full information`() {
        val variant = ShopifyProductVariant(
            id = "ID999",
            title = "Variant Title",
            sku = "SKU999",
            barcode = "BAR999",
            price = BigDecimal("9.99"),
            weight = ShopifyWeight(WeightUnit.KILOGRAMS, BigDecimal("2.5")),
            options = listOf(),
            mediaId = null
        )
        val expected = "ShopifyProductVariant(id='ID999', title='Variant Title', sku='SKU999', barcode='BAR999', price=9.99)"
        assertThat(variant.toString()).isEqualTo(expected)
    }
//
//    @Test
//    fun `test validation fails if multiple media present`() {
//        val mockVariant = mockk<ProductVariant>()
//        every { mockVariant.sku } returns "SKU"
//        every { mockVariant.barcode } returns "BARCODE"
//        every { mockVariant.price } returns "11.00"
//        every { mockVariant.inventoryItem.measurement.weight } returns
//                Weight.Builder().withUnit(WeightUnit.KILOGRAMS).withValue(BigDecimal("1.0")).build()
//        every { mockVariant.selectedOptions } returns listOf(
//            mockk<SelectedOption>().apply {
//                every { name } returns "Color"
//                every { value } returns "Green"
//                every { optionValue } returns ProductOptionValue("OPT1", "LMV1")
//            }
//        )
//        every { mockVariant.id } returns "IDX"
//        every { mockVariant.title } returns "Variant X"
//        every { mockVariant.media.edges } returns listOf(
//            mockk<MediaEdge>().apply {
//                every { node.id } returns "MEDIA1"
//            }
//        )
//        every { mockVariant.media.pageInfo.hasNextPage } returns true
//
//        assertThrows<IllegalArgumentException> { ShopifyProductVariant(mockVariant) }
//    }

    @Test
    fun `test dirty tracking`() {
        val option = ShopifyProductOptionValue("Material", "Wool")
        val variant = ShopifyProductVariant(
            id = "ID_DT",
            title = "Dirty Variant",
            sku = "SKU_DT",
            barcode = "BAR_DT",
            price = BigDecimal("5.00"),
            weight = ShopifyWeight(WeightUnit.KILOGRAMS, BigDecimal("1.0")),
            options = listOf(option),
            mediaId = null
        )
        // Initially, nothing is dirty
        assertThat(variant.dirtyTracker.getDirtyAndReset()).isFalse()
        // Change a tracked property
        variant.sku = "SKU_DT_NEW"
        assertThat(variant.dirtyTracker.getDirtyAndReset()).isTrue()
        // After reset, not dirty again
        assertThat(variant.dirtyTracker.getDirtyAndReset()).isFalse()
        // Change another property
        variant.price = BigDecimal("6.00")
        assertThat(variant.dirtyTracker.getDirtyAndReset()).isTrue()
        // Change to same value should not mark dirty
        variant.price = BigDecimal("6.00")
        assertThat(variant.dirtyTracker.getDirtyAndReset()).isFalse()
        // Change mediaId
        variant.mediaId = "MEDIA_DT"
        assertThat(variant.dirtyTracker.getDirtyAndReset()).isTrue()
        // Change to same value should not mark dirty
        variant.weight = ShopifyWeight(WeightUnit.KILOGRAMS, BigDecimal("1.0"))
        assertThat(variant.dirtyTracker.getDirtyAndReset()).isFalse()
        // Change weight
        variant.weight = ShopifyWeight(WeightUnit.GRAMS, BigDecimal("500.0"))
        assertThat(variant.dirtyTracker.getDirtyAndReset()).isTrue()
    }
//
//    @Test
//    fun `test dirty tracking of options`() {
//        val option = ShopifyProductVariantOption("Color", "Blue")
//        val variant = ShopifyProductVariant(
//            id = "ID_OPT",
//            title = "Option Variant",
//            sku = "SKU_OPT",
//            barcode = "BAR_OPT",
//            price = BigDecimal("10.00"),
//            weight = ShopifyWeight(WeightUnit.KILOGRAMS, BigDecimal("1.0")),
//            options = listOf(option),
//            mediaId = null
//        )
//        // Initially, nothing is dirty
//        assertThat(variant.dirtyTracker.getDirtyAndReset()).isFalse()
//        // Change option value
//        option.value = "Red"
//        assertThat(variant.dirtyTracker.getDirtyAndReset()).isTrue()
//        assertThat(variant.dirtyTracker.getDirtyAndReset()).isFalse()
//    }

    @Test
    fun `test value constructor`() {
        val option = ShopifyProductOptionValue("Material", "Cotton")
        val variant = ShopifyProductVariant(
            id = "V1",
            title = "Test Variant",
            sku = "SKU_V1",
            barcode = "BAR_V1",
            price = BigDecimal("15.99"),
            weight = ShopifyWeight(WeightUnit.GRAMS, BigDecimal("250.0")),
            options = listOf(option),
            mediaId = "MEDIA_V1"
        )
        assertThat(variant.id).isEqualTo("V1")
        assertThat(variant.title).isEqualTo("Test Variant")
        assertThat(variant.sku).isEqualTo("SKU_V1")
        assertThat(variant.barcode).isEqualTo("BAR_V1")
        assertThat(variant.price).isEqualByComparingTo("15.99")
        assertThat(variant.weight.unit).isEqualTo(WeightUnit.GRAMS)
        assertThat(variant.weight.value).isEqualTo(BigDecimal("250.00"))
        assertThat(variant.options).containsExactly(option)
        assertThat(variant.mediaId).isEqualTo("MEDIA_V1")
    }
}
