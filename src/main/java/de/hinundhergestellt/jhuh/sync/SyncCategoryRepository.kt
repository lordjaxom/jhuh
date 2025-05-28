package de.hinundhergestellt.jhuh.sync

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SyncCategoryRepository : JpaRepository<SyncCategory, UUID> {

    fun findByArtooId(artooId: Int): SyncCategory?
}
