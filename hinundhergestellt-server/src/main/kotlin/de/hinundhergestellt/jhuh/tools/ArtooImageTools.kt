package de.hinundhergestellt.jhuh.tools

import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import org.springframework.stereotype.Component

@Component
class ArtooImageTools(
    private val syncImageTools: SyncImageTools
) {
    fun findProductImages(vendorName: String, product: ArtooMappedProduct) =
        product.syncImageProductName?.let { syncImageTools.findProductImages(vendorName, it) } ?: listOf()

    fun findVariantImages(vendorName: String, product: ArtooMappedProduct) =
        product.syncImageProductName?.let { syncImageTools.findVariantImages(vendorName, it, product.variantSkus) } ?: listOf()
}

val ArtooMappedProduct.syncImageProductName get() = description.syncImageProductName.takeIf { it.isNotEmpty() }

val ArtooMappedProduct.variantSkus get() = variations.asSequence().mapNotNull { it.itemNumber }.toList()