package de.hinundhergestellt.jhuh.backend.syncdb

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.stream.Stream

@Repository
interface SyncProductRepository : JpaRepository<SyncProduct, UUID> {

    fun findAllBySyncedIsTrue(): Stream<SyncProduct>

    fun findByShopifyId(shopifyId: String): SyncProduct?

    fun findByArtooId(artooId: String): SyncProduct?

    fun findByVariantsBarcodeIn(barcode: List<String>): SyncProduct?

    fun existsByVendor(vendor: SyncVendor): Boolean
}
