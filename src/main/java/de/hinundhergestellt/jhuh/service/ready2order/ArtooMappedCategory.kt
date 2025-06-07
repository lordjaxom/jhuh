package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup

class ArtooMappedCategory internal constructor(
    private val group: ArtooProductGroup,
    val children: List<ArtooMappedCategory>,
    val products: List<ArtooMappedProduct>
) {
    val id by group::id
    val name by group::name

    fun findAllProducts(): Sequence<ArtooMappedProduct> =
        children.asSequence().flatMap { it.findAllProducts() } + products.asSequence()

    fun findProductByBarcode(barcode: String): ArtooMappedProduct? =
        products.firstOrNull { it.findVariationByBarcode(barcode) != null }
            ?: children.firstNotNullOfOrNull { it.findProductByBarcode(barcode) }

    fun findProductById(id: String): ArtooMappedProduct? =
        products.firstOrNull { it.id == id }
            ?: children.firstNotNullOfOrNull { it.findProductById(id) }

    fun findVariationByBarcode(barcode: String): ArtooMappedVariation? =
        products.firstNotNullOfOrNull { it.findVariationByBarcode(barcode) }
            ?: children.firstNotNullOfOrNull { it.findVariationByBarcode(barcode) }

    fun findCategoriesByProduct(product: ArtooMappedProduct): Sequence<ArtooMappedCategory> = sequence {
        var found = false
        children.asSequence()
            .flatMap { it.findCategoriesByProduct(product) }
            .onEach { found = true }
            .also { yieldAll(it) }
        if (found || products.contains(product)) {
            yield(this@ArtooMappedCategory)
        }
    }

    override fun toString() =
        "ArtooMappedCategory(id=$id, name='$name')"
}
