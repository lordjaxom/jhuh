package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.util.toRemoveProtectedMutableList
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Product
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductDeleteInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductStatus
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductUpdateInput

open class UnsavedShopifyProduct(
    var title: String,
    var vendor: String,
    var productType: String,
    var status: ProductStatus,
    var tags: Set<String>,
    open val options: List<UnsavedShopifyProductOption> = listOf(),
    open val metafields: List<ShopifyMetafield> = listOf()
) {
    override fun toString() =
        "UnsavedShopifyProduct(title='$title', vendor='$vendor', productType='$productType', status=$status)"

    internal fun toProductCreateInput() =
        ProductCreateInput(
            title = title,
            vendor = vendor,
            productType = productType,
            status = status,
            tags = tags.toList(),
            productOptions = options.map { option -> option.toOptionCreateInput() },
            metafields = metafields.map { metafield -> metafield.toMetafieldInput() }
        )
}

class ShopifyProduct : UnsavedShopifyProduct {

    val id: String
    val variants: MutableList<ShopifyProductVariant>
    val hasOnlyDefaultVariant: Boolean

    override val options: MutableList<ShopifyProductOption>
    override val metafields: MutableList<ShopifyMetafield>

    internal constructor(product: Product) : super(
        product.title,
        product.vendor,
        product.productType,
        product.status,
        product.tags.toSet()
    ) {
        require(!product.variants.pageInfo.hasNextPage) { "Product has more variants than were loaded" }
        require(!product.metafields.pageInfo.hasNextPage) { "Product has more metafields than were loaded" }

        id = product.id
        variants = product.variants.edges.asSequence().map { ShopifyProductVariant(it.node) }.toMutableList()
        hasOnlyDefaultVariant = product.hasOnlyDefaultVariant
        options = product.options.asSequence().map { ShopifyProductOption(it) }.toMutableList()
        metafields = product.metafields.edges.asSequence().map { ShopifyMetafield(it.node) }.toRemoveProtectedMutableList()
    }

    internal constructor(unsaved: UnsavedShopifyProduct, id: String, options: MutableList<ShopifyProductOption>) : super(
        unsaved.title,
        unsaved.vendor,
        unsaved.productType,
        unsaved.status,
        unsaved.tags
    ) {
        this.id = id
        variants = mutableListOf()
        hasOnlyDefaultVariant = options.isEmpty()
        this.options = options
        this.metafields = unsaved.metafields.toRemoveProtectedMutableList()
    }

    fun findVariantByBarcode(barcode: String) =
        variants.firstOrNull { it.barcode == barcode }

    override fun toString() =
        "ShopifyProduct(id='$id', title='$title', vendor='$vendor', productType='$productType', status=$status)"

    internal fun toProductUpdateInput() =
        ProductUpdateInput(
            id = id,
            title = title,
            vendor = vendor,
            productType = productType,
            status = status,
            tags = tags.toList(),
            metafields = metafields.map { it.toMetafieldInput() }
        )

    internal fun toProductDeleteInput() =
        ProductDeleteInput(id)
}