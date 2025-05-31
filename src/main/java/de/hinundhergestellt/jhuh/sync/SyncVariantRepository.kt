package de.hinundhergestellt.jhuh.sync

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*
import java.util.stream.Stream

@Repository
interface SyncVariantRepository : JpaRepository<SyncVariant, UUID> {

    fun findByBarcode(barcode: String): SyncVariant?
}
