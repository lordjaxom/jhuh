package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup

class ArtooMappedCategory internal constructor(
    private val group: ArtooProductGroup,
    val children: List<ArtooMappedCategory>,
    val products: List<ArtooMappedProduct>
) {
    val name by group::name

    fun containsReadyForSync(): Boolean =
        children.asSequence().any { it.containsReadyForSync() } || products.asSequence().any { it.isReadyForSync }

    fun findProductByBarcode(barcode: String): ArtooMappedProduct? =
        products.firstOrNull { it.findVariationByBarcode(barcode) != null }
            ?: children.firstNotNullOfOrNull { it.findProductByBarcode(barcode) }
}
