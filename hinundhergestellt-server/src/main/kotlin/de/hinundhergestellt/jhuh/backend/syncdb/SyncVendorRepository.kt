package de.hinundhergestellt.jhuh.backend.syncdb

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SyncVendorRepository : JpaRepository<SyncVendor, UUID> {

    fun findByNameIgnoreCase(name: String): SyncVendor?
}