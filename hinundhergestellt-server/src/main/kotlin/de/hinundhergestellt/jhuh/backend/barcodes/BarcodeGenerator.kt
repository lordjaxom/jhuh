package de.hinundhergestellt.jhuh.backend.barcodes

import org.krysalis.barcode4j.impl.upcean.UPCEANLogicImpl.calcChecksum
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class BarcodeGenerator {

    fun generate(): String {
        val generated = "${Random.nextLong(200_000_000_000, 299_999_999_999)}"
        val checksum = calcChecksum(generated)
        return generated + checksum
    }
}