package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.shopify.admin.types.Product
import com.shopify.admin.types.ProductCreateInput
import com.shopify.admin.types.ProductDeleteInput
import com.shopify.admin.types.ProductStatus
import com.shopify.admin.types.ProductUpdateInput
import de.hinundhergestellt.jhuh.util.RemoveProtectedMutableList
import de.hinundhergestellt.jhuh.util.toRemoveProtectedMutableList

open class UnsavedShopifyProduct(
    var title: String,
    var vendor: String,
    var productType: String,
    var status: ProductStatus,
    var tags: Set<String>,
    open val options: List<UnsavedShopifyProductOption>,
    open val metafields: List<ShopifyMetafield> = listOf()
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
            it.metafields = metafields.map { metafield -> metafield.toMetafieldInput() }
        }
}

class ShopifyProduct private constructor(
    val id: String,
    title: String,
    vendor: String,
    productType: String,
    status: ProductStatus,
    tags: Set<String>,
    override val options: MutableList<ShopifyProductOption>,
    val variants: MutableList<ShopifyProductVariant>,
    val hasOnlyDefaultVariant: Boolean,
    override val metafields: RemoveProtectedMutableList<ShopifyMetafield>
) : UnsavedShopifyProduct(
    title,
    vendor,
    productType,
    status,
    tags,
    options,
    metafields
) {
    internal constructor(product: Product) : this(
        product.id,
        product.title,
        product.vendor,
        product.productType,
        product.status,
        product.tags.toSet(),
        product.options.asSequence().map { ShopifyProductOption(it) }.toMutableList(),
        product.variants.edges.asSequence().map { ShopifyProductVariant(it.node) }.toMutableList(),
        product.hasOnlyDefaultVariant,
        product.metafields.edges.asSequence().map { ShopifyMetafield(it.node) }.toRemoveProtectedMutableList(),
    ) {
        require(!product.variants.pageInfo.hasNextPage) { "Product has more variants than were loaded" }
        require(!product.metafields.pageInfo.hasNextPage) { "Product has more metafields than were loaded" }
    }

    internal constructor(unsaved: UnsavedShopifyProduct, id: String, options: MutableList<ShopifyProductOption>) : this(
        id,
        unsaved.title,
        unsaved.vendor,
        unsaved.productType,
        unsaved.status,
        unsaved.tags,
        options,
        mutableListOf(),
        options.isEmpty(),
        unsaved.metafields.toRemoveProtectedMutableList()
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
            it.metafields = metafields.map { metafield -> metafield.toMetafieldInput() }
        }

    internal fun toProductDeleteInput() =
        ProductDeleteInput().also {
            it.id = id
        }
}