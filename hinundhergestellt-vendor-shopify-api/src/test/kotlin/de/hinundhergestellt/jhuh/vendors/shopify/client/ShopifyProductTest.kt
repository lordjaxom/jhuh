package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaImage
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Product
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductStatus
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
            descriptionHtml = "<p>Beschreibung</p>",
            hasOnlyDefaultVariant = false,
            tags = setOf("Tag1", "Tag2"),
            options = mutableListOf(option),
            metafields = mutableListOf(metafield),
            variants = mutableListOf(variant),
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
            descriptionHtml = "",
            hasOnlyDefaultVariant = false,
            tags = setOf(),
            options = mutableListOf(),
            metafields = mutableListOf(),
            variants = mutableListOf(),
            media = listOf()
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
            descriptionHtml = "",
            hasOnlyDefaultVariant = false,
            tags = setOf(),
            options = mutableListOf(),
            metafields = mutableListOf(),
            variants = mutableListOf(variant1, variant2),
            media = listOf()
        )
        assertThat(product.findVariantByBarcode("BAR2")).isEqualTo(variant2)
        assertThat(product.findVariantByBarcode("BAR3")).isNull()
    }

    @Test
    fun `test construction from Product`() {
        val mockProduct = mockk<Product> {
            every { id } returns "PRODID"
            every { title } returns "ProductTitle"
            every { vendor } returns "VendorX"
            every { productType } returns "TypeX"
            every { status } returns ProductStatus.ACTIVE
            every { descriptionHtml } returns "<p>Desc</p>"
            every { tags } returns listOf("TagA", "TagB")
            every { hasOnlyDefaultVariant } returns false
            every { options } returns listOf(
                mockk {
                    every { id } returns "OPTID"
                    every { name } returns "Farbe"
                    every { values } returns listOf("Rot", "Blau")
                }
            )
            every { metafields } returns mockk {
                every { edges } returns listOf(
                    mockk {
                        every { node } returns mockk {
                            every { namespace } returns "ns"
                            every { key } returns "key"
                            every { value } returns "val"
                            every { type } returns "single_line_text_field"
                        }
                    }
                )
                every { pageInfo } returns mockk {
                    every { hasNextPage } returns false
                }
            }
            every { media } returns mockk {
                every { edges } returns listOf(
                    mockk {
                        every { node } returns mockk<MediaImage> {
                            every { id } returns "MID"
                            every { image } returns mockk {
                                every { src } returns "SRC"
                                every { altText } returns "ALT"
                            }
                        }
                    }
                )
                every { pageInfo } returns mockk {
                    every { hasNextPage } returns false
                }
            }
        }
        val variant = mockk<ShopifyProductVariant>()
        val media = ShopifyMedia("MID", "SRC", "ALT")
        val product = ShopifyProduct(mockProduct, listOf(variant), listOf(media))
        assertThat(product.id).isEqualTo("PRODID")
        assertThat(product.title).isEqualTo("ProductTitle")
        assertThat(product.vendor).isEqualTo("VendorX")
        assertThat(product.productType).isEqualTo("TypeX")
        assertThat(product.status.name).isEqualTo("ACTIVE")
        assertThat(product.tags).containsExactlyInAnyOrder("TagA", "TagB")
        assertThat(product.options).hasSize(1)
        assertThat(product.options[0].id).isEqualTo("OPTID")
        assertThat(product.options[0].name).isEqualTo("Farbe")
        assertThat(product.options[0].values).containsExactly("Rot", "Blau")
        assertThat(product.metafields).hasSize(1)
        assertThat(product.metafields[0].namespace).isEqualTo("ns")
        assertThat(product.metafields[0].key).isEqualTo("key")
        assertThat(product.metafields[0].value).isEqualTo("val")
        assertThat(product.metafields[0].type).isEqualTo(ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD)
        assertThat(product.descriptionHtml).isEqualTo("<p>Desc</p>")
        assertThat(product.variants).containsExactly(variant)
        assertThat(product.media).containsExactly(media)
    }

    @Test
    fun `test construction from Product fails if multiple metafields present`() {
        val mockProduct = mockk<Product> {
            every { id } returns "PRODID"
            every { title } returns "ProductTitle"
            every { vendor } returns "VendorX"
            every { productType } returns "TypeX"
            every { status } returns ProductStatus.ACTIVE
            every { descriptionHtml } returns "<p>Desc</p>"
            every { tags } returns listOf("TagA", "TagB")
            every { hasOnlyDefaultVariant } returns false
            every { options } returns listOf()
            every { metafields } returns mockk {
                every { edges } returns listOf()
                every { pageInfo } returns mockk {
                    every { hasNextPage } returns true
                }
            }
            every { media } returns mockk {
                every { edges } returns listOf()
                every { pageInfo } returns mockk {
                    every { hasNextPage } returns false
                }
            }
        }

        assertThrows<IllegalArgumentException> { ShopifyProduct(mockProduct, listOf(), listOf()) }
    }

    @Test
    fun `test construction from UnsavedShopifyProduct`() {
        val unsaved = UnsavedShopifyProduct(
            title = "UnsavedTitle",
            vendor = "UnsavedVendor",
            productType = "UnsavedType",
            status = ProductStatus.DRAFT,
            descriptionHtml = "<p>unsaved</p>",
            tags = setOf("T1", "T2"),
            options = listOf(UnsavedShopifyProductOption("Size", listOf("S", "M"))),
            metafields = mutableListOf(ShopifyMetafield("ns", "key", "val", ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD)),
        )
        val options = listOf(ShopifyProductOption("OID", "Size", listOf("S", "M")))
        val product = ShopifyProduct(unsaved, "UNSAVEDID", options)
        assertThat(product.id).isEqualTo("UNSAVEDID")
        assertThat(product.title).isEqualTo("UnsavedTitle")
        assertThat(product.vendor).isEqualTo("UnsavedVendor")
        assertThat(product.productType).isEqualTo("UnsavedType")
        assertThat(product.status).isEqualTo(ProductStatus.DRAFT)
        assertThat(product.tags).containsExactlyInAnyOrder("T1", "T2")
        assertThat(product.options).hasSize(1)
        assertThat(product.options[0].id).isEqualTo("OID")
        assertThat(product.metafields).hasSize(1)
        assertThat(product.metafields[0].namespace).isEqualTo("ns")
        assertThat(product.descriptionHtml).isEqualTo("<p>unsaved</p>")
        assertThat(product.variants).isEmpty()
        assertThat(product.media).isEmpty()
    }
}