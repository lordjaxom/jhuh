package de.hinundhergestellt.jhuh

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties("hinundhergestellt")
data class HuhProperties(
    val imageDirectory: Path,
    val processingThreads: Int
)
