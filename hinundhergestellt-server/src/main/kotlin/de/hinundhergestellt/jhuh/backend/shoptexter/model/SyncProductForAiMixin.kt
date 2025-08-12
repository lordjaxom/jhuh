package de.hinundhergestellt.jhuh.backend.shoptexter.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.UUID

abstract class SyncProductForAiMixin {

    @get:JsonIgnore
    abstract val shopifyId: String?

    @get:JsonIgnore
    abstract val synced: Boolean

    @get:JsonIgnore
    abstract val descriptionHtml: String?

    @get:JsonIgnore
    abstract val id: UUID
}

abstract class SyncTechnicalDetailForAiMixin {

    @get:JsonIgnore
    abstract val sortOrder: Int
}