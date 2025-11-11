package de.hinundhergestellt.jhuh.usecases.shopify

import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVariant
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedVariation
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOptionValue
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.taxonomy.ShopifyCategoryTaxonomyProvider
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
            category = sync.shopifyCategory?.let { ShopifyCategoryTaxonomyProvider.categories[it]!! },
            hasOnlyDefaultVariant = artoo.hasOnlyDefaultVariant,
            tags = mappingService.allTags(sync, artoo) + "__NEW__",
            metafields = mappingService.productMetafields(sync),
        )

    fun map(sync: SyncVariant, artoo: ArtooMappedVariation) =
        ShopifyProductVariant(
            sku = artoo.itemNumber ?: "",
            barcode = artoo.barcode!!,
            price = artoo.price,
            weight = sync.weight!!,
            inventoryQuantity = artoo.stockValue.intValueExact(),
            options = listOfNotNull(
                if (artoo.parent.hasOnlyDefaultVariant) null
                else ShopifyProductOptionValue(sync.product.optionName!!, artoo.name)
            ),
            metafields = mappingService.variantMetafields()
        )
}