package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Media
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaImage

class ShopifyMedia(
    val id: String,
    val src: String,
    var altText: String,
) {
    internal constructor(mediaImage: MediaImage): this(
        id = mediaImage.id,
        src = mediaImage.image!!.src,
        altText = mediaImage.image!!.altText ?: ""
    )

    override fun toString() =
        "ShopifyMedia(id='$id', src='$src', altText='$altText')"

    internal fun toFileUpdateInput() =
        FileUpdateInput(
            id = id,
            alt = altText
        )
}

fun ShopifyMedia(media: Media) = when (media) {
    is MediaImage -> ShopifyMedia(media)
    else -> throw IllegalArgumentException("media")
}