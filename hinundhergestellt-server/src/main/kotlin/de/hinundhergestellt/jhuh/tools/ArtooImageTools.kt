package de.hinundhergestellt.jhuh.tools

import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import org.springframework.stereotype.Component

@Component
class ArtooImageTools(
    private val syncImageTools: SyncImageTools
) {
    fun findSyncImages(product: ArtooMappedProduct) =
        syncImageTools.findSyncImages(product.syncImageProductName, product.variantSkus)
}

val ArtooMappedProduct.syncImageProductName get() = description.syncImageProductName

val ArtooMappedProduct.variantSkus get() = variations.asSequence().mapNotNull { it.itemNumber }.toList()