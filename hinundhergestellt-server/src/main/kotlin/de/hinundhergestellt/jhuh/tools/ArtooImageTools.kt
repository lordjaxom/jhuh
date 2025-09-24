package de.hinundhergestellt.jhuh.tools

import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import org.springframework.stereotype.Component

@Component
class ArtooImageTools(
    private val syncImageTools: SyncImageTools
) {
    fun findProductImages(product: ArtooMappedProduct) =
        product.syncImageProductName?.let { syncImageTools.findProductImages(it) } ?: listOf()

    fun findVariantImages(product: ArtooMappedProduct) =
        product.syncImageProductName?.let { syncImageTools.findVariantImages(it, product.variantSkus) } ?: listOf()
}

val ArtooMappedProduct.syncImageProductName get() = description.syncImageProductName.takeIf { it.isNotEmpty() }

val ArtooMappedProduct.variantSkus get() = variations.asSequence().mapNotNull { it.itemNumber }.toList()