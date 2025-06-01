package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.Product
import com.shopify.admin.types.ProductCreateInput

open class UnsavedShopifyProduct(
    var title: String,
    var vendor: String,
    var productType: String,
    var tags: List<String>,
    open val options: List<UnsavedShopifyProductOption>
) {
    fun toProductCreateInput() =
        ProductCreateInput().also {
            it.title = title
            it.vendor = vendor
            it.productType = productType
            it.tags = tags
            it.productOptions = options.map { option -> option.toOptionCreateInput() }
        }
}

class ShopifyProduct private constructor(
    val id: String,
    title: String,
    vendor: String,
    productType: String,
    tags: List<String>,
    override val options: List<ShopifyProductOption>,
    val variants: MutableList<ShopifyProductVariant>,
) : UnsavedShopifyProduct(
    title,
    vendor,
    productType,
    tags,
    options
) {
    internal constructor(product: Product) : this(
        product.id,
        product.title,
        product.vendor,
        product.productType,
        product.tags,
        product.options.map { ShopifyProductOption(it) },
        product.variants.edges.asSequence().map { ShopifyProductVariant(it.node) }.toMutableList()
    )

    internal constructor(unsaved: UnsavedShopifyProduct, id: String, options: List<ShopifyProductOption>) : this(
        id,
        unsaved.title,
        unsaved.vendor,
        unsaved.productType,
        unsaved.tags,
        options,
        mutableListOf()
    )

    fun findVariantByBarcode(barcode: String) =
        variants.firstOrNull { it.barcode == barcode }
}