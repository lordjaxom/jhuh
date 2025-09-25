package de.hinundhergestellt.jhuh.usecases.shopify

import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionValue
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import org.springframework.stereotype.Component

@Component
class ShopifyMapper(
    private val shopifyDataStore: ShopifyDataStore,
    private val mappingService: MappingService
) {
    fun map(syncProduct: SyncProduct, artooProduct: ArtooMappedProduct) =
        UnsavedShopifyProduct(
            title = artooProduct.description,
            vendor = syncProduct.vendor!!.name,
            productType = syncProduct.type!!,
            descriptionHtml = syncProduct.descriptionHtml ?: "",
            tags = mappingService.allTags(syncProduct, artooProduct),
            options = listOf(), // options are added after saving
            metafields = mappingService.customMetafields(syncProduct),
        )

    fun map(sync: SyncVariant, artoo: ArtooMappedVariation) =
        ShopifyProductVariant(
            artoo.itemNumber!!,
            artoo.barcode!!,
            artoo.price,
            sync.weight!!,
            artoo.stockValue.intValueExact(),
            listOfNotNull(
                if (artoo.parent.hasOnlyDefaultVariant) null
                else ShopifyProductOptionValue(sync.product.optionName!!, artoo.name)
            )
        )
}