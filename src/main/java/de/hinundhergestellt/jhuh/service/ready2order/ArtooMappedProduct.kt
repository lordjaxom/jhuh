package de.hinundhergestellt.jhuh.service.ready2order

abstract class ArtooMappedProduct protected constructor(
    val variations: List<ArtooMappedVariation>
) {
    abstract val id: String
    abstract val name: String

    val isReadyForSync: Boolean
        get() = variations.all { it.barcode != null }

    val barcodes
        get() = variations.mapNotNull { it.barcode }

    fun findVariationByBarcode(barcode: String) =
        variations.firstOrNull { it.barcode == barcode }
}
