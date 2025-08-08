package de.hinundhergestellt.jhuh.backend.syncdb

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.OrderBy
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
    var artooId: String? = null,

    @Column(unique = true)
    var shopifyId: String? = null,

    @OneToOne(optional = true, fetch = FetchType.EAGER)
    var vendor: SyncVendor? = null,

    @Column
    var type: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    var tags: MutableSet<String> = mutableSetOf(),

    @Column(nullable = false)
    var synced: Boolean = false,

    @OneToMany(mappedBy = "product", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    val variants: MutableList<SyncVariant> = mutableListOf(),

    @Column(nullable = true, columnDefinition = "TEXT")
    var descriptionHtml: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderBy("sortOrder")
    val technicalDetails: MutableList<SyncTechnicalDetail> = mutableListOf(),

    @Id
    val id: UUID = UUID.randomUUID()
)

@Embeddable
class SyncTechnicalDetail(
    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val value: String,

    @Column(nullable = false)
    val sortOrder: Int
)
