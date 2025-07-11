package de.hinundhergestellt.jhuh.backend.shoptexter.model

import com.fasterxml.jackson.annotation.JsonIgnore
import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMedia
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant

abstract class ShopifyProductForAiMixin {

    @get:JsonIgnore
    abstract val dirtyTracker: DirtyTracker

    @get:JsonIgnore
    abstract val variants: List<ShopifyProductVariant>

    @get:JsonIgnore
    abstract val media: List<ShopifyMedia>
}