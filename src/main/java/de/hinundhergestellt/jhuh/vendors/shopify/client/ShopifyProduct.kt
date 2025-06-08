package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.shopify.admin.types.Product
import com.shopify.admin.types.ProductCreateInput
import com.shopify.admin.types.ProductStatus
import com.shopify.admin.types.ProductUpdateInput

open class UnsavedShopifyProduct(
    var title: String,
    var vendor: String,
    var productType: String,
    var status: ProductStatus,
    var tags: Set<String>,
    open val options: List<UnsavedShopifyProductOption>
) {
    override fun toString() =
        "UnsavedShopifyProduct(title='$title', vendor='$vendor', productType='$productType', status=$status)"

    internal fun toProductCreateInput() =
        ProductCreateInput().also {
            it.title = title
            it.vendor = vendor
            it.productType = productType
            it.status = status
            it.tags = tags.toList()
            it.productOptions = options.map { option -> option.toOptionCreateInput() }
        }
}

class ShopifyProduct private constructor(
    val id: String,
    title: String,
    vendor: String,
    productType: String,
    status: ProductStatus,
    tags: Set<String>,
    override val options: List<ShopifyProductOption>,
    val variants: MutableList<ShopifyProductVariant>,
) : UnsavedShopifyProduct(
    title,
    vendor,
    productType,
    status,
    tags,
    options
) {
    internal constructor(product: Product) : this(
        product.id,
        product.title,
        product.vendor,
        product.productType,
        product.status,
        product.tags.toSet(),
        product.options.map { ShopifyProductOption(it) },
        product.variants.edges.asSequence().map { ShopifyProductVariant(it.node) }.toMutableList()
    )

    internal constructor(unsaved: UnsavedShopifyProduct, id: String, options: List<ShopifyProductOption>) : this(
        id,
        unsaved.title,
        unsaved.vendor,
        unsaved.productType,
        unsaved.status,
        unsaved.tags,
        options,
        mutableListOf()
    )

    fun findVariantByBarcode(barcode: String) =
        variants.firstOrNull { it.barcode == barcode }

    override fun toString() =
        "ShopifyProduct(id='$id', title='$title', vendor='$vendor', productType='$productType', status=$status)"

    internal fun toProductUpdateInput() =
        ProductUpdateInput().also {
            it.id = id
            it.title = title
            it.vendor = vendor
            it.productType = productType
            it.status = status
            it.tags = tags.toList()
        }
}