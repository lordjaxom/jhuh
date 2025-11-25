package de.hinundhergestellt.jhuh.backend.syncdb

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SyncProductRepository : JpaRepository<SyncProduct, UUID> {

    fun findAllBySyncedIsTrue(): List<SyncProduct>

    fun findAllByGenerateTextsIsTrueAndArtooIdIsNotNull(): List<SyncProduct>

    fun findByShopifyId(shopifyId: String): SyncProduct?

    fun findByArtooId(artooId: String): SyncProduct?

    fun findByVariantsBarcodeIn(barcode: List<String>): SyncProduct?

    fun existsByVendor(vendor: SyncVendor): Boolean
}

// TODO: move to separate file
fun <T: Any, ID: Any> JpaRepository<T, ID>.update(id: ID, block: T.() -> Unit) =
    save(findById(id).orElseThrow().apply(block))
