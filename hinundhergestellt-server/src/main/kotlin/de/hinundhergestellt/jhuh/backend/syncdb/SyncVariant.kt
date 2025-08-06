package de.hinundhergestellt.jhuh.backend.syncdb

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(indexes = [
    Index(name = "idx_syncvariant_barcode", columnList = "barcode"),
    Index(name = "idx_syncvariant_artooid", columnList = "artooId"),
    Index(name = "idx_syncvariant_shopifyid", columnList = "shopifyId")
])
class SyncVariant(

    @ManyToOne(optional = false)
    val product: SyncProduct,

    @Column(nullable = false, unique = true)
    var barcode: String,

    @Column(unique = true)
    var artooId: Int? = null,

    @Column(unique = true)
    var shopifyId: String? = null,

    @Column
    var weight: BigDecimal? = null,

    @Id
    val id: UUID = UUID.randomUUID(),
)