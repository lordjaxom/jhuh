package de.hinundhergestellt.jhuh.backend.shoptexter.model

import com.fasterxml.jackson.annotation.JsonIgnore
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import java.util.UUID

abstract class SyncVariantForAiMixin {

    @get:JsonIgnore
    abstract val product: SyncProduct

    @get:JsonIgnore
    abstract val shopifyId: String?

    @get:JsonIgnore
    abstract val id: UUID
}