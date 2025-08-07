package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Weight
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ShopifyWeightTest {

    @Test
    fun `test creation from primary constructor`() {
        val weight = ShopifyWeight(WeightUnit.KILOGRAMS, BigDecimal("2.5"))
        assertThat(weight.unit).isEqualTo(WeightUnit.KILOGRAMS)
        assertThat(weight.value).isEqualTo(BigDecimal("2.5"))
    }

    @Test
    fun `test creation from Weight`() {
        val gqlWeight = Weight.Builder().withUnit(WeightUnit.GRAMS).withValue(BigDecimal("500.0")).build()
        val weight = ShopifyWeight(gqlWeight)
        assertThat(weight.unit).isEqualTo(WeightUnit.GRAMS)
        assertThat(weight.value).isEqualTo(BigDecimal("500.0"))
    }

    @Test
    fun `test toWeightInput`() {
        val weight = ShopifyWeight(WeightUnit.OUNCES, BigDecimal("3.0"))
        val input = weight.toWeightInput()
        assertThat(input).isInstanceOf(WeightInput::class.java)
        assertThat(input.unit).isEqualTo(WeightUnit.OUNCES)
        assertThat(input.value).isEqualTo(BigDecimal("3.0"))
    }

    @Test
    fun `test toString output`() {
        val weight = ShopifyWeight(WeightUnit.KILOGRAMS, BigDecimal("1.0"))
        val expected = "ShopifyWeight(unit=KILOGRAMS, value=1.0)"
        assertThat(weight.toString()).isEqualTo(expected)
    }
}

