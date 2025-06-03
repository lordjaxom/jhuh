package de.hinundhergestellt.jhuh.sync

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(
    indexes = [
        Index(name = "idx_syncproduct_artooid", columnList = "artooId"),
        Index(name = "idx_syncproduct_shopifyid", columnList = "shopifyId")
    ]
)
class SyncProduct(

    @Column(unique = true)
    var artooId: Int? = null,

    @Column(unique = true)
    var shopifyId: String? = null,

    @Column
    var vendor: String? = null,

    @Column
    var type: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    var tags: MutableSet<String> = mutableSetOf(),

    @Column(nullable = false)
    var synced: Boolean = false,

    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    val variants: MutableList<SyncVariant> = mutableListOf(),

    @Id
    val id: UUID = UUID.randomUUID()
)