package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductStatus
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ShopifyProductTest {

    @Test
    fun `test value constructor`() {
        val option = ShopifyProductOption("OPT1", "Color", listOf("Red", "Blue"))
        val metafield = ShopifyMetafield("ns", "key", "val", ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD)
        val variant = ShopifyProductVariant(
            id = "VID1",
            title = "Variant1",
            sku = "SKU1",
            barcode = "BAR1",
            price = BigDecimal.ONE,
            weight = ShopifyWeight(WeightUnit.GRAMS, 1.0),
            options = listOf(),
            mediaId = null
        )
        val media = ShopifyMedia("MID1", "SRC1", "ALT1")
        val product = ShopifyProduct(
            id = "PROD1",
            title = "Test Product",
            vendor = "Vendor1",
            productType = "Type1",
            status = ProductStatus.ACTIVE,
            tags = setOf("Tag1", "Tag2"),
            options = mutableListOf(option),
            metafields = mutableListOf(metafield),
            descriptionHtml = "<p>Beschreibung</p>",
            variants = mutableListOf(variant),
            hasOnlyDefaultVariant = false,
            media = listOf(media)
        )
        assertThat(product.id).isEqualTo("PROD1")
        assertThat(product.title).isEqualTo("Test Product")
        assertThat(product.vendor).isEqualTo("Vendor1")
        assertThat(product.productType).isEqualTo("Type1")
        assertThat(product.status).isEqualTo(ProductStatus.ACTIVE)
        assertThat(product.tags).containsExactlyInAnyOrder("Tag1", "Tag2")
        assertThat(product.options).hasSize(1)
        assertThat(product.options[0]).isEqualTo(option)
        assertThat(product.metafields).hasSize(1)
        assertThat(product.metafields[0]).isEqualTo(metafield)
        assertThat(product.descriptionHtml).isEqualTo("<p>Beschreibung</p>")
        assertThat(product.variants).hasSize(1)
        assertThat(product.variants[0]).isEqualTo(variant)
        assertThat(product.media).hasSize(1)
        assertThat(product.media[0]).isEqualTo(media)
    }

    @Test
    fun `test toString output`() {
        val product = ShopifyProduct(
            id = "IDSTR",
            title = "TStr",
            vendor = "VStr",
            productType = "PType",
            status = ProductStatus.ARCHIVED,
            options = mutableListOf(),
            metafields = mutableListOf(),
            variants = mutableListOf(),
            media = listOf(),
            tags = setOf(),
            descriptionHtml = "",
            hasOnlyDefaultVariant = false
        )
        val expected = "ShopifyProduct(id='IDSTR', title='TStr', vendor='VStr', productType='PType', status=ARCHIVED)"
        assertThat(product.toString()).isEqualTo(expected)
    }

    @Test
    fun `test findVariantByBarcode returns correct variant`() {
        val variant1 = ShopifyProductVariant(
            id = "VID1",
            title = "Variant1",
            sku = "SKU1",
            barcode = "BAR1",
            price = BigDecimal.ONE,
            weight = ShopifyWeight(WeightUnit.GRAMS, 1.0),
            options = listOf(),
            mediaId = null
        )
        val variant2 = ShopifyProductVariant(
            id = "VID2",
            title = "Variant2",
            sku = "SKU2",
            barcode = "BAR2",
            price = BigDecimal.ONE,
            weight = ShopifyWeight(WeightUnit.GRAMS, 1.0),
            options = listOf(),
            mediaId = null
        )
        val product = ShopifyProduct(
            id = "PROD2",
            title = "Test2",
            vendor = "Vendor2",
            productType = "Type2",
            status = ProductStatus.DRAFT,
            options = mutableListOf(),
            metafields = mutableListOf(),
            variants = mutableListOf(variant1, variant2),
            media = listOf(),
            tags = setOf(),
            descriptionHtml = "",
            hasOnlyDefaultVariant = false
        )
        assertThat(product.findVariantByBarcode("BAR2")).isEqualTo(variant2)
        assertThat(product.findVariantByBarcode("BAR3")).isNull()
    }
}
