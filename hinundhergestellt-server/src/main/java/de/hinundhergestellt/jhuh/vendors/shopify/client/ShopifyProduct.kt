package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.util.toRemoveProtectedMutableList
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.MediaImage
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Product
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductDeleteInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductStatus
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductUpdateInput
import java.util.concurrent.CopyOnWriteArrayList

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
    val images: List<ShopifyImage>

    override val options: MutableList<ShopifyProductOption>
    override val metafields: MutableList<ShopifyMetafield>

    internal constructor(product: Product, variants: List<ShopifyProductVariant>) : super(
        product.title,
        product.vendor,
        product.productType,
        product.status,
        product.tags.toSet()
    ) {
        require(!product.metafields.pageInfo.hasNextPage) { "Product has more metafields than were loaded" }
        require(!product.media.pageInfo.hasNextPage) { "Product has more medias than were loaded" }

        id = product.id
        this.variants = CopyOnWriteArrayList(variants)
        hasOnlyDefaultVariant = product.hasOnlyDefaultVariant
        images = product.media.edges.map { ShopifyImage(it.node as MediaImage) }
        options = CopyOnWriteArrayList(product.options.map { ShopifyProductOption(it) })
        metafields = product.metafields.edges.asSequence().map { ShopifyMetafield(it.node) }.toRemoveProtectedMutableList() // TODO
    }

    internal constructor(unsaved: UnsavedShopifyProduct, id: String, options: List<ShopifyProductOption>) : super(
        unsaved.title,
        unsaved.vendor,
        unsaved.productType,
        unsaved.status,
        unsaved.tags
    ) {
        this.id = id
        variants = CopyOnWriteArrayList()
        hasOnlyDefaultVariant = options.isEmpty()
        images = listOf()
        this.options = CopyOnWriteArrayList(options)
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