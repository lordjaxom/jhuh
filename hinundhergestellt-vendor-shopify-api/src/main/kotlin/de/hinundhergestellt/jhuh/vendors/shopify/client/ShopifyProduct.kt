package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Product
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductStatus
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.SEOInput
import de.hinundhergestellt.jhuh.vendors.shopify.taxonomy.ShopifyCategory
import de.hinundhergestellt.jhuh.vendors.shopify.taxonomy.ShopifyCategoryTaxonomyProvider
import java.time.OffsetDateTime

class ShopifyProduct private constructor(
    internal var internalId: String?,
    var handle: String?,
    var title: String,
    var vendor: String,
    var productType: String,
    var status: ProductStatus,
    var descriptionHtml: String,
    var seoTitle: String?,
    var seoDescription: String?,
    var createdAt: OffsetDateTime?,
    var category: ShopifyCategory?,
    val hasOnlyDefaultVariant: Boolean,
    var tags: Set<String>, // TODO: val
    val options: List<ShopifyProductOption>,
    val metafields: MutableList<ShopifyMetafield>,
    val variants: MutableList<ShopifyProductVariant>,
    val media: MutableList<ShopifyMedia>
) {
    val id get() = internalId!!

    constructor(
        title: String,
        vendor: String,
        productType: String,
        descriptionHtml: String,
        seoTitle: String?,
        seoDescription: String?,
        category: ShopifyCategory?,
        hasOnlyDefaultVariant: Boolean,
        tags: Set<String>,
        metafields: List<ShopifyMetafield>
    ) : this(
        internalId = null,
        handle = null,
        title = title,
        vendor = vendor,
        productType = productType,
        status = ProductStatus.DRAFT,
        descriptionHtml = descriptionHtml,
        seoTitle = seoTitle,
        seoDescription = seoDescription,
        createdAt = null,
        category = category,
        hasOnlyDefaultVariant = hasOnlyDefaultVariant,
        tags = tags,
        options = listOf(),
        metafields = metafields.toMutableList(),
        variants = mutableListOf(),
        media = mutableListOf()
    )

    internal constructor(product: Product, variants: List<ShopifyProductVariant>, media: List<ShopifyMedia>) : this(
        product.id,
        product.handle,
        product.title,
        product.vendor,
        product.productType,
        product.status,
        product.descriptionHtml,
        product.seo.title,
        product.seo.description,
        product.createdAt,
        product.category?.let { ShopifyCategoryTaxonomyProvider.categories[it.id]!! },
        product.hasOnlyDefaultVariant,
        product.tags.toSet(),
        product.options.map { ShopifyProductOption(it) },
        product.metafields.edges.asSequence().map { ShopifyMetafield(it.node) }.toMutableList(),
        variants.toMutableList(),
        media.toMutableList()
    ) {
        require(!product.metafields.pageInfo.hasNextPage) { "Product has more metafields than were loaded" }
    }

    fun findVariantByBarcode(barcode: String) = variants.firstOrNull { it.barcode == barcode }
    fun findVariantBySku(sku: String) = variants.firstOrNull { it.sku == sku }

    override fun toString() =
        "ShopifyProduct(id='$internalId', title='$title', vendor='$vendor', productType='$productType', status=$status)"

    internal fun toProductCreateInput(): ProductCreateInput {
        require(internalId == null) { "Cannot recreate existing product" }
        return ProductCreateInput(
            title = title,
            vendor = vendor,
            productType = productType,
            status = status,
            tags = tags.toList(),
            productOptions = options.map { it.toOptionCreateInput() },
            metafields = metafields.map { it.toMetafieldInput() },
            descriptionHtml = descriptionHtml,
            seo = toSEOInput(),
            category = category?.id
        )
    }

    internal fun toProductUpdateInput() =
        ProductUpdateInput(
            id = id,
            handle = handle,
            redirectNewHandle = true,
            title = title,
            vendor = vendor,
            productType = productType,
            status = status,
            tags = tags.toList(),
            metafields = metafields.map { it.toMetafieldInput() },
            descriptionHtml = descriptionHtml,
            seo = toSEOInput(),
            category = category?.id
        )

    private fun toSEOInput() =
        SEOInput(
            title = seoTitle,
            description = seoDescription
        )
}

val ShopifyProduct.variantSkus get() = variants.asSequence().map { it.sku }.filter { it.isNotEmpty() }.toList()