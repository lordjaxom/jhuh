package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.fixedScale
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.InventoryItemInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.InventoryItemMeasurementInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Weight
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import java.math.BigDecimal

private interface ShopifyProductVariantBase {

    var sku: String
    var barcode: String
    var price: BigDecimal
    var weight: Weight

    val options: List<ShopifyProductVariantOption>
}

class UnsavedShopifyProductVariant(
    override var sku: String,
    override var barcode: String,
    price: BigDecimal,
    override var weight: Weight = Weight(WeightUnit.GRAMS, 0.0),
    override val options: List<ShopifyProductVariantOption>
) : ShopifyProductVariantBase {

    override var price by fixedScale(price, 2)

    override fun toString() =
        "UnsavedShopifyProductVariant(sku='$sku', barcode='$barcode', price=$price)"

    internal fun toProductVariantsBulkInput() =
        ProductVariantsBulkInput(
            barcode = barcode,
            price = price.toPlainString(),
            optionValues = options.map { it.toVariantOptionValueInput() },
            inventoryItem = toInventoryItemInput(),
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

class ShopifyProductVariant internal constructor(
    private val unsaved: UnsavedShopifyProductVariant,
    val id: String,
    val title: String,
    var mediaId: String?
) : ShopifyProductVariantBase by unsaved {

    internal constructor(variant: ProductVariant) : this(
        UnsavedShopifyProductVariant(
            variant.sku ?: "",
            variant.barcode!!,
            BigDecimal(variant.price),
            variant.inventoryItem.measurement.weight!!,
            variant.selectedOptions.map { ShopifyProductVariantOption(it) }
        ),
        variant.id,
        variant.title,
        variant.media.edges.firstOrNull()?.node?.id
    ) {
        require(!variant.media.pageInfo.hasNextPage) { "ProductVariant has more media than is supported" }
    }

    override fun toString() =
        "ShopifyProductVariant(id='$id', sku='$sku', barcode='$barcode', title='$title')"

    fun toProductVariantsBulkInput() =
        ProductVariantsBulkInput(
            id = id,
            barcode = barcode,
            price = price.toPlainString(),
            optionValues = options.map { it.toVariantOptionValueInput() },
            inventoryItem = unsaved.toInventoryItemInput(),
            mediaId = mediaId
        )
}

fun Weight(unit: WeightUnit, value: Double) = Weight.Builder().withUnit(unit).withValue(value).build()

private fun Weight.toWeightInput() = WeightInput(value, unit)