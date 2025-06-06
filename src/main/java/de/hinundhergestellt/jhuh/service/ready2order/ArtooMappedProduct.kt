package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct

class ArtooMappedProduct internal constructor(
    private val product: ArtooProduct,
    variations: List<ArtooMappedVariation>
) {
    val variations = variations.ifEmpty { listOf(ArtooMappedVariation(null, product)) }

    val id by product::id
    val name by product::name
    val description by product::description
    val price by product::price
    val hasOnlyDefaultVariant = variations.isEmpty()

    val barcodes
        get() = variations.mapNotNull { it.barcode }

    fun findVariationByBarcode(barcode: String) =
        variations.firstOrNull { it.barcode == barcode }

    override fun toString(): String {
        return "ArtooMappedProduct(id=$id, name='$name', description='$description')"
    }
}
