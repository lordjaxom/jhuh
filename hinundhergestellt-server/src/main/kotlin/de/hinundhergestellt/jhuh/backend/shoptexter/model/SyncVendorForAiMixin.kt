package de.hinundhergestellt.jhuh.backend.shoptexter.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.UUID

abstract class SyncVendorForAiMixin {

    @get:JsonIgnore
    abstract val address: String?

    @get:JsonIgnore
    abstract val email: String?

    @get:JsonIgnore
    abstract val id: UUID
}