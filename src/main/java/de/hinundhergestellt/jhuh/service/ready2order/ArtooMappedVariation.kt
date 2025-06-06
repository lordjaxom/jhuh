package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct

class ArtooMappedVariation internal constructor(
    baseProduct: ArtooProduct?,
    private val product: ArtooProduct
) {
    val id by product::id
    val name by product::name
    val itemNumber by product::itemNumber
    val barcode by product::barcode
    val price = baseProduct?.let { product.price + it.price } ?: product.price

    override fun toString() =
        "ArtooMappedVariation(id=$id, name='$name', itemNumber='$itemNumber', barcode='$barcode')"
}
