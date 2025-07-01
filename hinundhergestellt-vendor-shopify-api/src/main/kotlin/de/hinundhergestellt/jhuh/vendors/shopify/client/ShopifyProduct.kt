package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.asRemoveProtectedMutableList
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
    open val metafields: MutableList<ShopifyMetafield> = mutableListOf(),
    var descriptionHtml: String = ""
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
            productOptions = options.map { it.toOptionCreateInput() },
            metafields = metafields.map { it.toMetafieldInput() },
            descriptionHtml = descriptionHtml
        )
}

class ShopifyProduct : UnsavedShopifyProduct {

    val id: String
    val variants: MutableList<ShopifyProductVariant>
    val hasOnlyDefaultVariant: Boolean
    val media: List<ShopifyMedia>

    override val options: MutableList<ShopifyProductOption>
    override val metafields: MutableList<ShopifyMetafield>

    internal constructor(product: Product, variants: List<ShopifyProductVariant>, media: List<ShopifyMedia>) : super(
        product.title,
        product.vendor,
        product.productType,
        product.status,
        product.tags.toSet(),
        descriptionHtml = product.descriptionHtml
    ) {
        require(!product.metafields.pageInfo.hasNextPage) { "Product has more metafields than were loaded" }
        require(!product.media.pageInfo.hasNextPage) { "Product has more medias than were loaded" }

        id = product.id
        this.variants = CopyOnWriteArrayList(variants)
        hasOnlyDefaultVariant = product.hasOnlyDefaultVariant
        this.media = CopyOnWriteArrayList(media)
        options = CopyOnWriteArrayList(product.options.map { ShopifyProductOption(it) })
        metafields = CopyOnWriteArrayList(product.metafields.edges.map { ShopifyMetafield(it.node) }).asRemoveProtectedMutableList()
    }

    internal constructor(unsaved: UnsavedShopifyProduct, id: String, options: List<ShopifyProductOption>) : super(
        unsaved.title,
        unsaved.vendor,
        unsaved.productType,
        unsaved.status,
        unsaved.tags,
        descriptionHtml = unsaved.descriptionHtml
    ) {
        this.id = id
        variants = CopyOnWriteArrayList()
        hasOnlyDefaultVariant = options.isEmpty()
        media = listOf()
        this.options = CopyOnWriteArrayList(options)
        this.metafields = CopyOnWriteArrayList(unsaved.metafields).asRemoveProtectedMutableList()
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