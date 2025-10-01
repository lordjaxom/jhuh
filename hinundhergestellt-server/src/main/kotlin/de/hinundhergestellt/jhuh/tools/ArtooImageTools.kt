package de.hinundhergestellt.jhuh.tools

import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import org.springframework.stereotype.Component

@Component
class ArtooImageTools(
    private val syncImageTools: SyncImageTools
) {
    fun findProductImages(vendorName: String, product: ArtooMappedProduct) =
        product.productNameForImages?.let { syncImageTools.findProductImages(vendorName, it) } ?: listOf()

    fun findVariantImages(vendorName: String, product: ArtooMappedProduct) =
        product.productNameForImages?.let { syncImageTools.findVariantImages(vendorName, it, product.variantSkus) } ?: listOf()
}

val ArtooMappedProduct.productNameForImages get() = description.takeIf { it.isNotEmpty() }?.run { productNameForImages }

val ArtooMappedProduct.variantSkus get() = variations.asSequence().mapNotNull { it.itemNumber }.toList()