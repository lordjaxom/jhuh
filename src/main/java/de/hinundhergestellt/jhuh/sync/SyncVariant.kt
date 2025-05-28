package de.hinundhergestellt.jhuh.sync

import jakarta.persistence.*
import java.util.*

@Entity
@Table(indexes = [Index(name = "idx_syncvariant_barcode", columnList = "barcode")])
class SyncVariant(

    @ManyToOne(optional = false)
    val product: SyncProduct,

    @Column(nullable = false, unique = true)
    val barcode: String,

    @Column(nullable = false)
    var deleted: Boolean = false,

    @Id
    val id: UUID = UUID.randomUUID(),
)