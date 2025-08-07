package de.hinundhergestellt.jhuh.vendors.ready2order.datastore

import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroup

class ArtooMappedCategory internal constructor(
    private val group: ArtooProductGroup,
    val children: List<ArtooMappedCategory>,
    val products: List<ArtooMappedProduct>
) {
    val id by group::id
    val name by group::name

    fun findAllProducts(): Sequence<ArtooMappedProduct> =
        children.asSequence().flatMap { it.findAllProducts() } + products.asSequence()

    fun findProductsByBarcodes(barcodes: List<String>): Sequence<ArtooMappedProduct> =
        products.asSequence().filter { it.barcodes.any { barcode -> barcode in barcodes } } +
                children.flatMap { it.findProductsByBarcodes(barcodes) }

    fun findProductById(id: String): ArtooMappedProduct? =
        products.firstOrNull { it.id == id }
            ?: children.firstNotNullOfOrNull { it.findProductById(id) }

    fun findVariationById(id: Int): ArtooMappedVariation? =
        products.firstNotNullOfOrNull { it.findVariationById(id) }
            ?: children.firstNotNullOfOrNull { it.findVariationById(id) }

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
