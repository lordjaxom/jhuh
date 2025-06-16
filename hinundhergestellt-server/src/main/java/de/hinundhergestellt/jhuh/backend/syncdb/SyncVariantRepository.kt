package de.hinundhergestellt.jhuh.backend.syncdb

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SyncVariantRepository : JpaRepository<SyncVariant, UUID> {

    fun findByBarcode(barcode: String): SyncVariant?
}
