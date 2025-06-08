package de.hinundhergestellt.jhuh.backend.syncdb

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(indexes = [Index(name = "idx_syncvariant_barcode", columnList = "barcode")])
class SyncVariant(

    @ManyToOne(optional = false)
    val product: SyncProduct,

    @Column(nullable = false, unique = true)
    val barcode: String,

    @Id
    val id: UUID = UUID.randomUUID(),
)