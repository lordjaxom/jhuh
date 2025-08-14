package de.hinundhergestellt.jhuh.vendors.ready2order.datastore

import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroup

sealed class ArtooMappedProduct protected constructor(
    val variations: List<ArtooMappedVariation>
) {
    abstract val id: String
    abstract var name: String
    abstract var description: String
    abstract val hasOnlyDefaultVariant: Boolean

    val barcodes
        get() = variations.mapNotNull { variation -> variation.barcode?.takeIf { it.isNotEmpty() } }

    init {
        variations.forEach { it.parent = this }
    }

    fun findVariationById(id: Int) =
        variations.firstOrNull { it.id == id }

    fun findVariationByBarcode(barcode: String) =
        variations.firstOrNull { it.barcode == barcode }

    override fun toString() =
        "ArtooMappedProduct(id='$id', name='$name', description='$description')"

    internal class Group(
        internal val group: ArtooProductGroup,
        variations: List<ArtooMappedVariation>
    ) : ArtooMappedProduct(variations) {

        override val id = "group-${group.id}"
        override var name
            get() = group.name
            set(value) {
                group.name = value
                variations.forEach { it.product.name = "$value (${it.product.alternativeNameInPos})" }
            }
        override var description by group::description
        override val hasOnlyDefaultVariant = false
    }

    internal class Single(
        internal val product: ArtooProduct
    ) : ArtooMappedProduct(listOf(ArtooMappedVariation(product, true))) {

        override val id = "product-${product.id}"
        override var name by product::name
        override var description by product::description
        override val hasOnlyDefaultVariant = true
    }
}