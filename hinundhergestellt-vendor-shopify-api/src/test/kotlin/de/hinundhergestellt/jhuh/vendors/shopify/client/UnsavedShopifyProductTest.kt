package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnsavedShopifyProductTest {

    @Test
    fun `test creation`() {
        val unsaved = UnsavedShopifyProduct(
            title = "Test Product",
            vendor = "Test Vendor",
            productType = "Test Type",
            status = ProductStatus.ACTIVE,
            tags = setOf("Tag1", "Tag2"),
            options = listOf(UnsavedShopifyProductOption("Color", listOf("Red", "Blue"))),
            metafields = mutableListOf(ShopifyMetafield("ns", "key", "val", ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD)),
            descriptionHtml = "<p>Beschreibung</p>"
        )
        assertThat(unsaved.title).isEqualTo("Test Product")
        assertThat(unsaved.vendor).isEqualTo("Test Vendor")
        assertThat(unsaved.productType).isEqualTo("Test Type")
        assertThat(unsaved.status).isEqualTo(ProductStatus.ACTIVE)
        assertThat(unsaved.tags).containsExactlyInAnyOrder("Tag1", "Tag2")
        assertThat(unsaved.options).hasSize(1)
        assertThat(unsaved.metafields).hasSize(1)
        assertThat(unsaved.descriptionHtml).isEqualTo("<p>Beschreibung</p>")
    }

    @Test
    fun `test toProductCreateInput`() {
        val unsaved = UnsavedShopifyProduct(
            title = "CreateTitle",
            vendor = "CreateVendor",
            productType = "CreateType",
            status = ProductStatus.DRAFT,
            tags = setOf("A", "B"),
            options = listOf(UnsavedShopifyProductOption("Size", listOf("S", "M"))),
            metafields = mutableListOf(ShopifyMetafield("ns2", "k2", "v2", ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD)),
            descriptionHtml = "DescHtml"
        )
        val input = unsaved.toProductCreateInput()
        assertThat(input.title).isEqualTo("CreateTitle")
        assertThat(input.vendor).isEqualTo("CreateVendor")
        assertThat(input.productType).isEqualTo("CreateType")
        assertThat(input.status).isEqualTo(ProductStatus.DRAFT)
        assertThat(input.tags).containsExactlyInAnyOrder("A", "B")
        assertThat(input.productOptions).hasSize(1)
        assertThat(input.metafields).hasSize(1)
        assertThat(input.descriptionHtml).isEqualTo("DescHtml")
    }

    @Test
    fun `test toString output`() {
        val unsaved = UnsavedShopifyProduct(
            title = "StrTitle",
            vendor = "StrVendor",
            productType = "StrType",
            status = ProductStatus.ARCHIVED,
            tags = setOf("X", "Y"),
            options = emptyList(),
            metafields = mutableListOf(),
            descriptionHtml = "StrDesc"
        )
        val expected = "UnsavedShopifyProduct(title='StrTitle', vendor='StrVendor', productType='StrType', status=ARCHIVED)"
        assertThat(unsaved.toString()).isEqualTo(expected)
    }
}

