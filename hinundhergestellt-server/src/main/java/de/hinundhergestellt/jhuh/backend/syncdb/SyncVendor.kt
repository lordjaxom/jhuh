package de.hinundhergestellt.jhuh.backend.syncdb

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(indexes = [
    Index(name="idx_syncvendor_name", columnList = "name")
])
class SyncVendor(

    @Column(nullable = false, unique = true)
    var name: String,

    @Column
    var address: String?,

    @Column
    var email: String?,

    @Id
    val id: UUID = UUID.randomUUID()
)