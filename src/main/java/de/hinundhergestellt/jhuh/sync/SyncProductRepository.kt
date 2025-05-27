package de.hinundhergestellt.jhuh.sync

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*
import java.util.stream.Stream

@Repository
interface SyncProductRepository : JpaRepository<SyncProduct, UUID> {

    fun findAllBy(): Stream<SyncProduct>

    fun findByShopifyId(shopifyId: String): SyncProduct?

    fun deleteByArtooId(artooId: String)
}
