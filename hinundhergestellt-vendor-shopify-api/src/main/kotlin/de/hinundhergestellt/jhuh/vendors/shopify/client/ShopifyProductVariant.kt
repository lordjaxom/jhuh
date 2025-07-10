package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.DirtyTracker
import de.hinundhergestellt.jhuh.core.HasDirtyTracker
import de.hinundhergestellt.jhuh.core.fixedScale
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.InventoryItemInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.InventoryItemMeasurementInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.InventoryLevelInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkInput
import java.math.BigDecimal

private interface ShopifyProductVariantCommonFields {

    var sku: String
    var barcode: String
    var price: BigDecimal
    var weight: ShopifyWeight

    val options: List<ShopifyProductVariantOption>
}

internal class BaseShopifyProductVariant(
    override var sku: String,
    override var barcode: String,
    price: BigDecimal,
    override var weight: ShopifyWeight,
    override val options: List<ShopifyProductVariantOption>
) : ShopifyProductVariantCommonFields {

    override var price by fixedScale(price, 2)

    internal constructor(variant: ProductVariant) : this(
        variant.sku ?: "",
        variant.barcode!!,
        BigDecimal(variant.price),
        ShopifyWeight(variant.inventoryItem.measurement.weight!!),
        variant.selectedOptions.map { ShopifyProductVariantOption(it) }
    )

    internal fun toInventoryItemInput() =
        InventoryItemInput(
            sku = sku,
            tracked = true,
            measurement = toInventoryItemMeasurementInput()
        )

    private fun toInventoryItemMeasurementInput() =
        InventoryItemMeasurementInput(weight.toWeightInput())
}

class UnsavedShopifyProductVariant private constructor(
    internal val base: BaseShopifyProductVariant,
    val inventoryLocationId: String,
    val inventoryQuantity: Int
) : ShopifyProductVariantCommonFields by base {

    constructor(
        sku: String,
        barcode: String,
        price: BigDecimal,
        weight: ShopifyWeight,
        inventoryLocationId: String,
        inventoryQuantity: Int,
        options: List<ShopifyProductVariantOption>
    ) : this(
        BaseShopifyProductVariant(
            sku,
            barcode,
            price,
            weight,
            options
        ),
        inventoryLocationId,
        inventoryQuantity
    )

    override fun toString() =
        "UnsavedShopifyProductVariant(sku='$sku', barcode='$barcode', price=$price)"

    internal fun toProductVariantsBulkInput() =
        ProductVariantsBulkInput(
            barcode = barcode,
            price = price.toPlainString(),
            optionValues = options.map { it.toVariantOptionValueInput() },
            inventoryItem = base.toInventoryItemInput(),
            inventoryQuantities = listOf(toInventoryLevelInput())
        )

    private fun toInventoryLevelInput() =
        InventoryLevelInput(
            locationId = inventoryLocationId,
            availableQuantity = inventoryQuantity
        )
}

class ShopifyProductVariant private constructor(
    private val base: BaseShopifyProductVariant,
    val id: String,
    val title: String,
    mediaId: String?
) : ShopifyProductVariantCommonFields, HasDirtyTracker {

    override val dirtyTracker = DirtyTracker()

    override var sku by dirtyTracker.track(base::sku)
    override var barcode by dirtyTracker.track(base::barcode)
    override var price by dirtyTracker.track(base::price)
    override var weight by dirtyTracker.track(base::weight)
    override val options by base::options

    var mediaId by dirtyTracker.track(mediaId)

    internal constructor(variant: ProductVariant) : this(
        BaseShopifyProductVariant(variant),
        variant.id,
        variant.title,
        variant.media.edges.firstOrNull()?.node?.id
    ) {
        require(!variant.media.pageInfo.hasNextPage) { "ProductVariant has more media than is supported" }
    }

    internal constructor(unsaved: UnsavedShopifyProductVariant, id: String, title: String) : this(
        unsaved.base,
        id,
        title,
        null
    )

    internal constructor(
        id: String,
        title: String,
        sku: String,
        barcode: String,
        price: BigDecimal,
        weight: ShopifyWeight,
        options: List<ShopifyProductVariantOption>,
        mediaId: String?
    ) : this(
        BaseShopifyProductVariant(
            sku,
            barcode,
            price,
            weight,
            options
        ),
        id,
        title,
        mediaId
    )

    override fun toString() =
        "ShopifyProductVariant(id='$id', title='$title', sku='$sku', barcode='$barcode', price=$price)"

    internal fun toProductVariantsBulkInput() =
        ProductVariantsBulkInput(
            id = id,
            barcode = barcode,
            price = price.toPlainString(),
            optionValues = options.map { it.toVariantOptionValueInput() },
            inventoryItem = base.toInventoryItemInput(),
            mediaId = mediaId
        )
}