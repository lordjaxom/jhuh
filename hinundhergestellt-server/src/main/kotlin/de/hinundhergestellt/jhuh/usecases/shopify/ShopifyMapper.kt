package de.hinundhergestellt.jhuh.usecases.shopify

import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionValue
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import org.springframework.stereotype.Component

@Component
class ShopifyMapper(
    private val mappingService: MappingService
) {
    fun map(sync: SyncProduct, artoo: ArtooMappedProduct) =
        ShopifyProduct(
            title = artoo.description,
            vendor = sync.vendor!!.name,
            productType = sync.type!!,
            descriptionHtml = sync.descriptionHtml ?: "",
            seoTitle = sync.seoTitle,
            seoDescription = sync.metaDescription,
            hasOnlyDefaultVariant = artoo.hasOnlyDefaultVariant,
            tags = mappingService.allTags(sync, artoo),
            metafields = mappingService.customMetafields(sync),
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