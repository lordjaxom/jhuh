package de.hinundhergestellt.jhuh.sync

import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedProduct
import jakarta.persistence.*
import org.springframework.lang.Nullable
import java.util.*

@Entity
@Table(
    indexes = [
        Index(name = "idx_syncproduct_artooid", columnList = "artooId"),
        Index(name = "idx_syncproduct_shopifyid", columnList = "shopifyId")
    ]
)
class SyncProduct(

    @Column
    var artooId: String? = null,

    @Column
    var shopifyId: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    val tags: MutableSet<String> = mutableSetOf(),

    @Column(nullable = false)
    var synced: Boolean = false,

    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    val variants: MutableList<SyncVariant> = mutableListOf(),

    @Id
    val id: UUID = UUID.randomUUID()
)