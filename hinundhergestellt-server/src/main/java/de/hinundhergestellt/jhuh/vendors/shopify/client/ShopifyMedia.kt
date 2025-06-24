package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Media
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaImage

class ShopifyMedia(
    val id: String,
    val src: String
) {
    internal constructor(mediaImage: MediaImage): this(
        id = mediaImage.id,
        src = mediaImage.image!!.src
    )

    override fun toString() =
        "ShopifyMedia(id='$id', src='$src')"
}

fun ShopifyMedia(media: Media) = when (media) {
    is MediaImage -> ShopifyMedia(media)
    else -> throw IllegalArgumentException("media")
}