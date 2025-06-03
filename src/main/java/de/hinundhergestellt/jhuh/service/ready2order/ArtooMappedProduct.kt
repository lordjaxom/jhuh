package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup
import kotlin.getValue

sealed class ArtooMappedProduct protected constructor(
    val variations: List<ArtooMappedVariation>
) {
    abstract val id: String
    abstract val name: String
    abstract val description: String
    abstract val hasOnlyDefaultVariant: Boolean

    val barcodes
        get() = variations.mapNotNull { it.barcode }

    fun findVariationByBarcode(barcode: String) =
        variations.firstOrNull { it.barcode == barcode }

    override fun toString(): String {
        return "ArtooMappedProduct(id='$id', name='$name', description='$description')"
    }

    internal class Group(
        private val group: ArtooProductGroup,
        variations: List<ArtooMappedVariation>
    ) : ArtooMappedProduct(variations) {

        override val id = "group-${group.id}"
        override val name by group::name
        override val description by group::description
        override val hasOnlyDefaultVariant = false
    }

    internal class Single(
        private val product: ArtooProduct
    ) : ArtooMappedProduct(listOf(ArtooMappedVariation(product))) {

        override val id = "product-${product.id}"
        override val name by product::name
        override val description by product::description
        override val hasOnlyDefaultVariant = true
    }

    internal class Variations(
        private val product: ArtooProduct,
        variations: List<ArtooMappedVariation>
    ) : ArtooMappedProduct(variations) {

        override val id = "product-${product.id}"
        override val name by product::name
        override val description by product::description
        override val hasOnlyDefaultVariant = false
    }
}
