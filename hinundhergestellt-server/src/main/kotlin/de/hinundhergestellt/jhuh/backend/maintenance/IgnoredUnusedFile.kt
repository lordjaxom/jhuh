package de.hinundhergestellt.jhuh.backend.maintenance

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.util.UUID

@Entity
class IgnoredUnusedFile(

    @Column(nullable = false, unique = true)
    val fileName: String,

    @Id
    val id: UUID = UUID.randomUUID()
)
