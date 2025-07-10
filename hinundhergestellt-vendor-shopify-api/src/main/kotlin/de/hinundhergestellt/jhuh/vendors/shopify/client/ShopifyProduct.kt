package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.asRemoveProtectedMutableList
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Product
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductDeleteInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductStatus
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductUpdateInput
import java.util.concurrent.CopyOnWriteArrayList

private interface ShopifyProductCommonFields {

    var title: String
    var vendor: String
    var productType: String
    var status: ProductStatus
    var tags: Set<String>
    var descriptionHtml: String
}

internal class BaseShopifyProduct(
    override var title: String,
    override var vendor: String,
    override var productType: String,
    override var status: ProductStatus,
    override var tags: Set<String>,
    override var descriptionHtml: String,
) : ShopifyProductCommonFields {

    internal constructor(product: Product) : this(
        product.title,
        product.vendor,
        product.productType,
        product.status,
        product.tags.toSet(),
        product.descriptionHtml
    )
}

class UnsavedShopifyProduct private constructor(
    internal val base: BaseShopifyProduct,
    val options: List<UnsavedShopifyProductOption>,
    val metafields: MutableList<ShopifyMetafield>,
) : ShopifyProductCommonFields by base {

    constructor(
        title: String,
        vendor: String,
        productType: String,
        status: ProductStatus,
        tags: Set<String>,
        options: List<UnsavedShopifyProductOption> = listOf(),
        metafields: MutableList<ShopifyMetafield> = mutableListOf(),
        descriptionHtml: String = ""
    ) : this(
        BaseShopifyProduct(
            title,
            vendor,
            productType,
            status,
            tags,
            descriptionHtml
        ),
        options,
        metafields
    )

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

class ShopifyProduct private constructor(
    private val base: BaseShopifyProduct,
    val id: String,
    val variants: MutableList<ShopifyProductVariant>,
    val hasOnlyDefaultVariant: Boolean,
    val media: List<ShopifyMedia>,
    val options: MutableList<ShopifyProductOption>,
    val metafields: MutableList<ShopifyMetafield>,
) : ShopifyProductCommonFields by base {

    internal constructor(product: Product, variants: List<ShopifyProductVariant>, media: List<ShopifyMedia>) : this(
        BaseShopifyProduct(product),
        product.id,
        CopyOnWriteArrayList(variants),
        product.hasOnlyDefaultVariant,
        CopyOnWriteArrayList(media),
        CopyOnWriteArrayList(product.options.map { ShopifyProductOption(it) }),
        CopyOnWriteArrayList(product.metafields.edges.map { ShopifyMetafield(it.node) }).asRemoveProtectedMutableList()
    ) {
        require(!product.metafields.pageInfo.hasNextPage) { "Product has more metafields than were loaded" }
        require(!product.media.pageInfo.hasNextPage) { "Product has more medias than were loaded" }
    }

    internal constructor(unsaved: UnsavedShopifyProduct, id: String, options: List<ShopifyProductOption>) : this(
        unsaved.base,
        id,
        CopyOnWriteArrayList(),
        options.isEmpty(),
        listOf(),
        CopyOnWriteArrayList(options),
        CopyOnWriteArrayList(unsaved.metafields).asRemoveProtectedMutableList()
    )

    internal constructor(
        id: String,
        title: String,
        vendor: String,
        productType: String,
        status: ProductStatus,
        tags: Set<String>,
        options: MutableList<ShopifyProductOption>,
        metafields: MutableList<ShopifyMetafield>,
        descriptionHtml: String,
        variants: MutableList<ShopifyProductVariant>,
        hasOnlyDefaultVariant: Boolean,
        media: List<ShopifyMedia>
    ) : this(
        BaseShopifyProduct(
            title,
            vendor,
            productType,
            status,
            tags,
            descriptionHtml
        ),
        id,
        variants,
        hasOnlyDefaultVariant,
        media,
        options,
        metafields
    )

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