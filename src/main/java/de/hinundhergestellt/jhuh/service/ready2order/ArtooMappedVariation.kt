package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct

class ArtooMappedVariation internal constructor(
    baseProduct: ArtooProduct?,
    private val product: ArtooProduct
) {
    val name by product::name
    val itemNumber by product::itemNumber
    val barcode by product::barcode
    val price = baseProduct?.let { product.price + it.price } ?: product.price

    override fun toString(): String {
        return "ArtooMappedVariation(name='$name', barcode=$barcode, itemNumber=$itemNumber)"
    }
}
