package de.hinundhergestellt.jhuh.sync

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.stream.Stream

@Repository
interface SyncCategoryRepository : JpaRepository<SyncCategory, UUID> {

    fun findByArtooId(artooId: Int): SyncCategory?

    fun findByArtooIdIn(artooIds: Collection<Int>): Stream<SyncCategory>
}
