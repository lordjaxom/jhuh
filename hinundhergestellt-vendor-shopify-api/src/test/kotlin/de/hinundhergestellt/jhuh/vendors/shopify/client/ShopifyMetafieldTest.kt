package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Metafield
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShopifyMetafieldTest {

    @Test
    fun `test creation from primary constructor`() {
        val metafield = ShopifyMetafield("ns", "key", "val", ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD)
        assertThat(metafield.namespace).isEqualTo("ns")
        assertThat(metafield.key).isEqualTo("key")
        assertThat(metafield.value).isEqualTo("val")
        assertThat(metafield.type).isEqualTo(ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD)
    }

    @Test
    fun `test creation from Metafield`() {
        val gqlMetafield = mockk<Metafield>() {
            every { namespace } returns "ns"
            every { key } returns "key"
            every { value } returns "val"
            every { type } returns "single_line_text_field"
        }
        val metafield = ShopifyMetafield(gqlMetafield)
        assertThat(metafield.namespace).isEqualTo("ns")
        assertThat(metafield.key).isEqualTo("key")
        assertThat(metafield.value).isEqualTo("val")
        assertThat(metafield.type).isEqualTo(ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD)
    }

    @Test
    fun `test toString output`() {
        val metafield = ShopifyMetafield("ns", "key", "val", ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD)
        val expected = "ShopifyMetafield(namespace='ns', key='key', value='val', type=MULTI_LINE_TEXT_FIELD)"
        assertThat(metafield.toString()).isEqualTo(expected)
    }

    @Test
    fun `test dirty tracking`() {
        val metafield = ShopifyMetafield("ns", "key", "val", ShopifyMetafieldType.SINGLE_LINE_TEXT_FIELD)
        assertThat(metafield.dirtyTracker.getDirtyAndReset()).isFalse()
        metafield.value = "newval"
        assertThat(metafield.dirtyTracker.getDirtyAndReset()).isTrue()
        assertThat(metafield.dirtyTracker.getDirtyAndReset()).isFalse()
        metafield.type = ShopifyMetafieldType.MULTI_LINE_TEXT_FIELD
        assertThat(metafield.dirtyTracker.getDirtyAndReset()).isTrue()
    }
}

