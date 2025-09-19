package de.hinundhergestellt.jhuh.backend.shoptexter.model

import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import org.springframework.stereotype.Component

@Component
class ProductMapper(
    private val mappingService: MappingService
) {

    fun map(artoo: ArtooMappedProduct, sync: SyncProduct, description: String?) =
        Product(
            name = artoo.name,
            title = description?.takeIf { it.isNotEmpty() } ?: artoo.description,
            description = sync.descriptionHtml ?: "",
            productType = sync.type ?: "",
            vendor = sync.vendor?.name ?: "",
            tags = sync.tags,
            technicalDetails = sync.technicalDetails.associate { it.name to it.value },
            hasOnlyDefaultVariant = artoo.hasOnlyDefaultVariant,
            variants =
                if (!artoo.hasOnlyDefaultVariant) artoo.variations.map { it.name }
                else listOf(),
        )

    fun map(shopify: ShopifyProduct) =
        Product(
            name = shopify.title.substringBefore(",").trim(),
            title = shopify.title,
            description = shopify.descriptionHtml,
            productType = shopify.productType,
            vendor = shopify.vendor,
            tags = shopify.tags,
            technicalDetails = mappingService.extractTechnicalDetails(shopify),
            hasOnlyDefaultVariant = shopify.hasOnlyDefaultVariant,
            variants =
                if (!shopify.hasOnlyDefaultVariant) shopify.variants.map { it.title }
                else listOf()
        )
}