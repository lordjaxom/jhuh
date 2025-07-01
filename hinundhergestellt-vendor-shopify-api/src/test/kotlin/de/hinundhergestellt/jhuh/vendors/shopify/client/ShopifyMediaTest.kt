package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Image
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Media
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaImage
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ShopifyMediaTest {

    @Test
    fun `test creation from primary constructor`() {
        val media = ShopifyMedia(
            id = "MEDIA1",
            src = "https://example.com/image.jpg",
            altText = "Alt Text"
        )
        assertThat(media.id).isEqualTo("MEDIA1")
        assertThat(media.src).isEqualTo("https://example.com/image.jpg")
        assertThat(media.altText).isEqualTo("Alt Text")
    }

    @Test
    fun `test creation from MediaImage`() {
        val image = mockk<Image>()
        every { image.src } returns "https://img.com/1.jpg"
        every { image.altText } returns "Test Alt"
        val mediaImage = mockk<MediaImage>()
        every { mediaImage.id } returns "MID1"
        every { mediaImage.image } returns image

        val media = ShopifyMedia(mediaImage)
        assertThat(media.id).isEqualTo("MID1")
        assertThat(media.src).isEqualTo("https://img.com/1.jpg")
        assertThat(media.altText).isEqualTo("Test Alt")
    }

    @Test
    fun `test creation from MediaImage with null altText`() {
        val image = mockk<Image>()
        every { image.src } returns "https://img.com/2.jpg"
        every { image.altText } returns null
        val mediaImage = mockk<MediaImage>()
        every { mediaImage.id } returns "MID2"
        every { mediaImage.image } returns image

        val media = ShopifyMedia(mediaImage)
        assertThat(media.altText).isEqualTo("")
    }

    @Test
    fun `test toString output`() {
        val media = ShopifyMedia("IDSTR", "SRCSTR", "ALTSTR")
        val expected = "ShopifyMedia(id='IDSTR', src='SRCSTR', altText='ALTSTR')"
        assertThat(media.toString()).isEqualTo(expected)
    }

    @Test
    fun `test toFileUpdateInput`() {
        val media = ShopifyMedia("IDFUI", "SRCFUI", "ALT-FUI")
        val input = media.toFileUpdateInput()
        assertThat(input).isInstanceOf(FileUpdateInput::class.java)
        assertThat(input.id).isEqualTo("IDFUI")
        assertThat(input.alt).isEqualTo("ALT-FUI")
    }

    @Test
    fun `test ShopifyMedia factory with MediaImage`() {
        val image = mockk<Image>()
        every { image.src } returns "https://img.com/3.jpg"
        every { image.altText } returns "ALT3"
        val mediaImage = mockk<MediaImage>()
        every { mediaImage.id } returns "MID3"
        every { mediaImage.image } returns image

        val media = ShopifyMedia(mediaImage as Media)
        assertThat(media.id).isEqualTo("MID3")
        assertThat(media.src).isEqualTo("https://img.com/3.jpg")
        assertThat(media.altText).isEqualTo("ALT3")
    }

    @Test
    fun `test ShopifyMedia factory throws on unknown Media type`() {
        val unknownMedia = mockk<Media>()
        assertThrows<IllegalArgumentException> {
            ShopifyMedia(unknownMedia)
        }
    }
}

