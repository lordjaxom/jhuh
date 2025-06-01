package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.Product
import com.shopify.admin.types.ProductCreateInput

class ShopifyProduct private constructor(
    var id: String?,
    var title: String,
    var vendor: String,
    var productType: String,
    var tags: List<String>,
    val options: List<ShopifyProductOption>,
    val variants: MutableList<ShopifyProductVariant>,
) {
    constructor(
        title: String,
        vendor: String,
        productType: String,
        tags: List<String>,
        options: List<ShopifyProductOption>
    ) : this(
        null,
        title,
        vendor,
        productType,
        tags,
        options,
        mutableListOf()
    )

    internal constructor(product: Product) : this(
        product.id,
        product.title,
        product.vendor,
        product.productType,
        product.tags,
        product.options.map { ShopifyProductOption(it) },
        product.variants.edges.asSequence().map { ShopifyProductVariant(it.node) }.toMutableList()
    )

    fun findVariantByBarcode(barcode: String) =
        variants.firstOrNull { it.barcode == barcode }

    fun toProductCreateInput() =
        ProductCreateInput().also {
            it.title = title
            it.vendor = vendor
            it.productType = productType
            it.tags = tags
            it.productOptions = options.map { option -> option.toOptionCreateInput() }
        }
}