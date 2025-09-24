package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.core.asRemoveProtectedMutableList
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Product
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductDeleteInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductStatus
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductUpdateInput
import java.util.concurrent.CopyOnWriteArrayList

interface ShopifyProductCommonFields {

    var title: String
    var vendor: String
    var productType: String
    var status: ProductStatus
    var descriptionHtml: String
    var tags: Set<String>
}

internal class BaseShopifyProduct(
    override var title: String,
    override var vendor: String,
    override var productType: String,
    override var status: ProductStatus,
    override var descriptionHtml: String,
    override var tags: Set<String>,
) : ShopifyProductCommonFields {

    internal constructor(product: Product) : this(
        product.title,
        product.vendor,
        product.productType,
        product.status,
        product.descriptionHtml,
        product.tags.toSet()
    )
}

class UnsavedShopifyProduct private constructor(
    internal val base: BaseShopifyProduct,
    val options: List<ShopifyProductOption>,
    val metafields: MutableList<ShopifyMetafield>,
) : ShopifyProductCommonFields by base {

    constructor(
        title: String,
        vendor: String,
        productType: String,
        status: ProductStatus = ProductStatus.DRAFT,
        descriptionHtml: String = "",
        tags: Set<String> = setOf(),
        options: List<ShopifyProductOption> = listOf(),
        metafields: MutableList<ShopifyMetafield> = mutableListOf()
    ) : this(
        BaseShopifyProduct(
            title,
            vendor,
            productType,
            status,
            descriptionHtml,
            tags
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
    val hasOnlyDefaultVariant: Boolean,
    options: MutableList<ShopifyProductOption>,
    metafields: MutableList<ShopifyMetafield>,
    val variants: MutableList<ShopifyProductVariant>, // not dirty tracked, separate update workflow
    val media: List<ShopifyMedia>,
) : ShopifyProductCommonFields, HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    override var title by dirtyTracker.track(base::title)
    override var vendor by dirtyTracker.track(base::vendor)
    override var productType by dirtyTracker.track(base::productType)
    override var status by dirtyTracker.track(base::status)
    override var descriptionHtml by dirtyTracker.track(base::descriptionHtml)
    override var tags by dirtyTracker.track(base::tags)

    val options by dirtyTracker.track(options)
    val metafields by dirtyTracker.track(metafields)

    internal constructor(product: Product, variants: List<ShopifyProductVariant>, media: List<ShopifyMedia>) : this(
        BaseShopifyProduct(product),
        product.id,
        product.hasOnlyDefaultVariant,
        CopyOnWriteArrayList(product.options.map { ShopifyProductOption(it) }),
        CopyOnWriteArrayList(product.metafields.edges.map { ShopifyMetafield(it.node) }).asRemoveProtectedMutableList(),
        CopyOnWriteArrayList(variants),
        media
    ) {
        require(!product.metafields.pageInfo.hasNextPage) { "Product has more metafields than were loaded" }
    }

    internal constructor(unsaved: UnsavedShopifyProduct, id: String) : this(
        unsaved.base,
        id,
        unsaved.options.isEmpty(),
        CopyOnWriteArrayList(unsaved.options),
        CopyOnWriteArrayList(unsaved.metafields).asRemoveProtectedMutableList(),
        CopyOnWriteArrayList(),
        listOf()
    )

    internal constructor(
        id: String,
        title: String,
        vendor: String,
        productType: String,
        status: ProductStatus,
        descriptionHtml: String,
        hasOnlyDefaultVariant: Boolean,
        tags: Set<String>,
        options: MutableList<ShopifyProductOption>,
        metafields: MutableList<ShopifyMetafield>,
        variants: MutableList<ShopifyProductVariant>,
        media: List<ShopifyMedia>
    ) : this(
        BaseShopifyProduct(
            title,
            vendor,
            productType,
            status,
            descriptionHtml,
            tags
        ),
        id,
        hasOnlyDefaultVariant,
        options,
        metafields,
        variants,
        media
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
            metafields = metafields.map { it.toMetafieldInput() },
            descriptionHtml = descriptionHtml
        )

    internal fun toProductDeleteInput() =
        ProductDeleteInput(id)
}

val ShopifyProduct.variantSkus get() = variants.asSequence().map { it.sku }.filter { it.isNotEmpty() }.toList()