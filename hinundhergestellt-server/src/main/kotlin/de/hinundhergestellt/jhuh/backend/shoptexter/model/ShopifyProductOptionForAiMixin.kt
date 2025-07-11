package de.hinundhergestellt.jhuh.backend.shoptexter.model

import com.fasterxml.jackson.annotation.JsonIgnore
import de.hinundhergestellt.jhuh.core.DirtyTracker

abstract class ShopifyProductOptionForAiMixin {

    @get:JsonIgnore
    abstract val dirtyTracker: DirtyTracker
}