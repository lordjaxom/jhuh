package de.hinundhergestellt.jhuh.sync

import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(indexes = [Index(name = "idx_synccategory_artooid", columnList = "artooId")])
class SyncCategory(

    @Column(nullable = false, unique = true)
    val artooId: Int,

    @ElementCollection(fetch = FetchType.EAGER)
    var tags: MutableSet<String> = mutableSetOf(),

    @Id
    val id: UUID = UUID.randomUUID(),
)