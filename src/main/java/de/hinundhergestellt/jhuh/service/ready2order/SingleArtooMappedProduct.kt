package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct

class SingleArtooMappedProduct internal constructor(
    private val product: ArtooProduct
) : ArtooMappedProduct(listOf(ArtooMappedVariation(product))) {

    override val id = "single-${product.id}"
    override val name by product::name
    override val description by product::description
}
