package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.File
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Media
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaImage
import java.net.URI

data class ShopifyMedia(
    val id: String,
    val src: URI,
    var altText: String,
) {
    val fileName = src.path.substringAfterLast("/")

    internal constructor(mediaImage: MediaImage) : this(
        id = mediaImage.id,
        src = URI(mediaImage.image!!.src),
        altText = mediaImage.image!!.altText ?: ""
    )

    override fun toString() =
        "ShopifyMedia(id='$id', src='$src', altText='$altText')"

    internal fun toFileUpdateInput() =
        FileUpdateInput(
            id = id,
            alt = altText
        )

    internal fun toFileUpdateInput(referencesToAdd: List<String>? = null, referencesToRemove: List<String>? = null) =
        FileUpdateInput(
            id = id,
            alt = altText,
            filename = fileName,
            referencesToAdd = referencesToAdd,
            referencesToRemove = referencesToRemove
        )
}

fun ShopifyMedia(media: Media) =
    when (media) {
        is MediaImage -> ShopifyMedia(media)
        else -> throw IllegalArgumentException("media")
    }

fun ShopifyMedia(media: File) =
    when (media) {
        is MediaImage -> ShopifyMedia(media)
        else -> throw IllegalArgumentException("media")
    }