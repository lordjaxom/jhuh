package de.hinundhergestellt.jhuh.sync

import jakarta.persistence.*
import org.springframework.lang.Nullable
import java.util.*

@Entity
@Table(indexes = [Index(name = "idx_syncproduct_shopifyid", columnList = "shopifyId")])
class SyncProduct(

    @Column
    var shopifyId: String?,

    @ElementCollection(fetch = FetchType.EAGER)
    val tags: List<String>,

    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    val variants: MutableList<SyncVariant> = mutableListOf(),

    @Id
    val id: UUID = UUID.randomUUID()
)

