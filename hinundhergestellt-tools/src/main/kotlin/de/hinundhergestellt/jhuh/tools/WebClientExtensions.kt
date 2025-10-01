package de.hinundhergestellt.jhuh.tools

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import reactor.core.scheduler.Schedulers
import java.io.OutputStream
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

suspend fun WebClient.downloadFileTo(uri: URI, output: OutputStream) {
    val body = this.get()
        .uri(uri)
        .retrieve()
        .bodyToFlux<DataBuffer>()
        .publishOn(Schedulers.boundedElastic())
    DataBufferUtils.write(body, output)
        .doOnNext { DataBufferUtils.release(it) }
        .then()
        .awaitSingleOrNull()
}

suspend fun WebClient.downloadFileTo(uri: URI, target: Path) {
    var success = false
    try {
        target.outputStream().use { downloadFileTo(uri, it) }
        success = true
    } finally {
        if (!success) target.deleteIfExists()
    }
}