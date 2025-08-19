package de.hinundhergestellt.jhuh.tools

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import reactor.core.scheduler.Schedulers
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

@Component
class MediaDownloadWebClient {

    private val client = WebClient.builder()
        .codecs { it.defaultCodecs().maxInMemorySize(5 * 1024 * 1024) }
        .build()

    suspend fun downloadFileTo(url: String, target: Path) {
        var success = false
        try {
            target.outputStream().use { output ->
                val body = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToFlux<DataBuffer>()
                    .publishOn(Schedulers.boundedElastic())
                DataBufferUtils.write(body, output)
                    .doOnNext { DataBufferUtils.release(it) }
                    .then()
                    .awaitSingleOrNull()
            }
            success = true
        } finally {
            if (!success) {
                target.deleteIfExists()
            }
        }
    }
}