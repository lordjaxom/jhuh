package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Product
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductStatus
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductUpdateInput

class ShopifyProduct private constructor(
    internal var internalId: String?,
    var title: String,
    var vendor: String,
    var productType: String,
    var status: ProductStatus,
    var descriptionHtml: String,
    val hasOnlyDefaultVariant: Boolean,
    var tags: Set<String>, // TODO: val
    val options: List<ShopifyProductOption>,
    val metafields: MutableList<ShopifyMetafield>,
    val variants: MutableList<ShopifyProductVariant>,
    val media: MutableList<ShopifyMedia>,
) {
    val id get() = internalId!!

    constructor(
        title: String,
        vendor: String,
        productType: String,
        descriptionHtml: String,
        hasOnlyDefaultVariant: Boolean,
        tags: Set<String>,
        metafields: List<ShopifyMetafield>
    ) : this(
        internalId =  null,
        title = title,
        vendor = vendor,
        productType = productType,
        status = ProductStatus.DRAFT,
        descriptionHtml = descriptionHtml,
        hasOnlyDefaultVariant = hasOnlyDefaultVariant,
        tags = tags,
        options = listOf(),
        metafields = metafields.toMutableList(),
        variants = mutableListOf(),
        media = mutableListOf()
    )

    internal constructor(product: Product, variants: List<ShopifyProductVariant>, media: List<ShopifyMedia>) : this(
        product.id,
        product.title,
        product.vendor,
        product.productType,
        product.status,
        product.descriptionHtml,
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
            descriptionHtml = descriptionHtml
        )
    }

    internal fun toProductUpdateInput() =
        ProductUpdateInput(
            id = id,
            title = title,
            vendor = vendor,
            productType = productType,
            status = status,
            tags = tags.toList(),
            metafields = metafields.map { it.toMetafieldInput() },
            descriptionHtml = descriptionHtml
        )
}

val ShopifyProduct.variantSkus get() = variants.asSequence().map { it.sku }.filter { it.isNotEmpty() }.toList()