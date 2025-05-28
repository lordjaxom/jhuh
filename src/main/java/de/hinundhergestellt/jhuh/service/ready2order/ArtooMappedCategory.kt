package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup

class ArtooMappedCategory internal constructor(
    private val group: ArtooProductGroup,
    val children: List<ArtooMappedCategory>,
    val products: List<ArtooMappedProduct>
) {
    val id by group::id
    val name by group::name

    fun containsReadyForSync(): Boolean =
        children.asSequence().any { it.containsReadyForSync() } || products.asSequence().any { it.isReadyForSync }

    fun findProductByBarcode(barcode: String): ArtooMappedProduct? =
        products.firstOrNull { it.findVariationByBarcode(barcode) != null }
            ?: children.firstNotNullOfOrNull { it.findProductByBarcode(barcode) }

    fun findProductById(id: String): ArtooMappedProduct? =
        products.firstOrNull { it.id == id }
            ?: children.firstNotNullOfOrNull { it.findProductById(id) }

    fun findAllCategoriesByProduct(product: ArtooMappedProduct): Sequence<ArtooMappedCategory> = sequence {
        var found = false
        children.asSequence()
            .flatMap { it.findAllCategoriesByProduct(product) }
            .onEach { found = true }
            .also { yieldAll(it) }
        if (found || products.contains(product)) {
            yield(this@ArtooMappedCategory)
        }
    }
}
