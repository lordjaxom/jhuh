package de.hinundhergestellt.jhuh.vendors.shopify.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.*

class UnsavedShopifyProductVariantTest {

    @Test
    fun `test creation`() {
        val variant = UnsavedShopifyProductVariant(
            sku = "SKU111",
            barcode = "BAR111",
            price = BigDecimal("10.00"),
            weight = ShopifyWeight(WeightUnit.GRAMS, BigDecimal("500.0")),
            options = listOf(ShopifyProductOptionValue("Material", "Cotton")),
            inventoryLocationId = "LOC001",
            inventoryQuantity = 20
        )

        assertThat(variant.sku).isEqualTo("SKU111")
        assertThat(variant.barcode).isEqualTo("BAR111")
        assertThat(variant.price).isEqualByComparingTo("10.00")
        assertThat(variant.weight.unit).isEqualTo(WeightUnit.GRAMS)
        assertThat(variant.options).hasSize(1) // TODO
        assertThat(variant.inventoryLocationId).isEqualTo("LOC001")
        assertThat(variant.inventoryQuantity).isEqualTo(20)
    }

    @Test
    fun `test toProductVariantsBulkInput`() {
        val variant = UnsavedShopifyProductVariant(
            sku = "SKU222",
            barcode = "BAR222",
            price = BigDecimal("19.95"),
            weight = ShopifyWeight(WeightUnit.KILOGRAMS, BigDecimal("1.2")),
            options = listOf(ShopifyProductOptionValue("Style", "Modern")),
            inventoryLocationId = "LOC002",
            inventoryQuantity = 15
        )

        val input = variant.toProductVariantsBulkInput()
        assertThat(input.id).isNull()
        assertThat(input.barcode).isEqualTo("BAR222")
        assertThat(input.price).isEqualTo("19.95")
        assertThat(input.optionValues).hasSize(1) // TODO
        assertThat(input.inventoryItem?.sku).isEqualTo("SKU222")
        assertThat(input.inventoryItem?.tracked).isTrue()
        assertThat(input.inventoryItem?.measurement?.weight?.unit).isEqualTo(WeightUnit.KILOGRAMS)
        assertThat(input.inventoryItem?.measurement?.weight?.value).isEqualTo(BigDecimal("1.20"))
        assertThat(input.inventoryQuantities).hasSize(1)
        assertThat(input.inventoryQuantities?.get(0)?.locationId).isEqualTo("LOC002")
        assertThat(input.inventoryQuantities?.get(0)?.availableQuantity).isEqualTo(15)
        assertThat(input.mediaId).isNull()
    }

    @Test
    fun `test toString output`() {
        val variant = UnsavedShopifyProductVariant(
            sku = "SKU333",
            barcode = "BAR333",
            price = BigDecimal("7.50"),
            weight = ShopifyWeight(WeightUnit.OUNCES, BigDecimal("8.0")),
            options = emptyList(),
            inventoryLocationId = "LOC003",
            inventoryQuantity = 5
        )

        val expected = "UnsavedShopifyProductVariant(sku='SKU333', barcode='BAR333', price=7.50)"
        assertThat(variant.toString()).isEqualTo(expected)
    }
}
