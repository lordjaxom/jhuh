package de.hinundhergestellt.jhuh.backend.shoptexter.model

import com.fasterxml.jackson.annotation.JsonIgnore
import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.LinkedMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOptionValue

abstract class ShopifyProductOptionForAiMixin {

    @get:JsonIgnore
    abstract val dirtyTracker: DirtyTracker

    @get:JsonIgnore
    abstract val linkedMetafield: LinkedMetafield?

    @get:JsonIgnore
    abstract val optionValues: List<ProductOptionValue>
}