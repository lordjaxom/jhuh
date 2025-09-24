package de.hinundhergestellt.jhuh.tools

import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import org.springframework.stereotype.Component

@Component
class ArtooImageTools(
    private val syncImageTools: SyncImageTools
) {
    fun findProductImages(product: ArtooMappedProduct) =
        syncImageTools.findProductImages(product.syncImageProductName)

    fun findVariantImages(product: ArtooMappedProduct) =
        syncImageTools.findVariantImages(product.syncImageProductName, product.variantSkus)

    fun findAllImages(product: ArtooMappedProduct) =
        syncImageTools.findAllImages(product.syncImageProductName, product.variantSkus)
}

val ArtooMappedProduct.syncImageProductName get() = description.syncImageProductName

val ArtooMappedProduct.variantSkus get() = variations.asSequence().mapNotNull { it.itemNumber }.toList()