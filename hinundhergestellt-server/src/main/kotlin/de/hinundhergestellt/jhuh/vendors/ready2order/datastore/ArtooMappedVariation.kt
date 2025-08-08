package de.hinundhergestellt.jhuh.vendors.ready2order.datastore

import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProduct

class ArtooMappedVariation internal constructor(
    internal val product: ArtooProduct,
    val isDefaultVariant: Boolean
) {
    val id by product::id
    var name by product::alternativeNameInPos
    val itemNumber by product::itemNumber
    val barcode by product::barcode
    val price by product::price
    val stockValue by product::stockValue

    override fun toString() =
        "ArtooMappedVariation(id=$id, name='$name', itemNumber='$itemNumber', barcode='$barcode')"
}