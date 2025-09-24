package de.hinundhergestellt.jhuh.usecases.shopify

import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import org.springframework.stereotype.Component

@Component
class ShopifyProductMapper(
    private val mappingService: MappingService
) {
    fun mapToProduct(syncProduct: SyncProduct, artooMappedProduct: ArtooMappedProduct): UnsavedShopifyProduct =
        Builder(syncProduct, artooMappedProduct).build()

    private open inner class Builder(
        protected val syncProduct: SyncProduct,
        protected val artooProduct: ArtooMappedProduct
    ) {
        fun build() =
            UnsavedShopifyProduct(
                title = artooProduct.description,
                vendor = syncProduct.vendor!!.name,
                productType = syncProduct.type!!,
                descriptionHtml = syncProduct.descriptionHtml ?: "",
                tags = tags(),
                options = options(),
                metafields = metafields(),
            )

        protected fun tags() = mappingService.allTags(syncProduct, artooProduct)
        protected fun metafields() = mappingService.customMetafields(syncProduct)

        private fun options() = buildList {
            if (!artooProduct.hasOnlyDefaultVariant)
                add(ShopifyProductOption(syncProduct.optionName!!, artooProduct.variations.map { it.name }))
        }
    }
}