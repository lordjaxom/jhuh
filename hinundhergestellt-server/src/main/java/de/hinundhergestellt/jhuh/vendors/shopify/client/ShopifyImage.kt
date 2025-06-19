package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaImage

class ShopifyImage(
    val mediaId: String,
    val src: String
) {
    internal constructor(image: MediaImage): this(
        mediaId = image.id,
        src = image.image!!.src
    )

    override fun toString() =
        "ShopifyImage(id='$mediaId', src='$src')"
}