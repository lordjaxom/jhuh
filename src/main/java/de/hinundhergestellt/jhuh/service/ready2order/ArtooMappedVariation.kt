package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct
import kotlin.jvm.optionals.getOrNull

class ArtooMappedVariation internal constructor(
    private val product: ArtooProduct
) {
    val name by product::name

    val barcode: String?
        get() = product.barcode.getOrNull()
}
