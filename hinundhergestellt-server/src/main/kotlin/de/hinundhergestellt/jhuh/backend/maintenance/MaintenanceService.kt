package de.hinundhergestellt.jhuh.backend.maintenance

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class MaintenanceService(
    private val ignoredUnusedFileRepository: IgnoredUnusedFileRepository
) {

    fun isUnusedFileIgnored(fileName: String) = ignoredUnusedFileRepository.existsByFileName(fileName)

    @Transactional
    fun ignoreUnusedFile(fileNames: List<String>) {
        fileNames.forEach { ignoredUnusedFileRepository.save(IgnoredUnusedFile(it))}
    }
}