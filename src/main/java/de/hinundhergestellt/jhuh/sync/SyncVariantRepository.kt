package de.hinundhergestellt.jhuh.sync

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SyncVariantRepository : JpaRepository<SyncVariant, UUID> {

    fun existsByBarcode(barcode: String): Boolean

    fun findByBarcode(barcode: String): SyncVariant?
}
