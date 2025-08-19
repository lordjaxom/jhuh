package de.hinundhergestellt.jhuh.backend.maintenance

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface IgnoredUnusedFileRepository : JpaRepository<IgnoredUnusedFile, UUID> {

    fun existsByFileName(fileName: String): Boolean
}