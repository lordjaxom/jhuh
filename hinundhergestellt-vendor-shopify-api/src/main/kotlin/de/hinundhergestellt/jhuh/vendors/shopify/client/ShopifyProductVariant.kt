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

open class UnsavedShopifyProductVariant(
    var sku: String,
    var barcode: String,
    price: BigDecimal,
    val options: List<ShopifyProductVariantOption>,
    var weight: Weight = Weight(WeightUnit.GRAMS, 0.0)
) {
    var price by fixedScale(price, 2)

    override fun toString() =
        "UnsavedShopifyProductVariant(sku='$sku', barcode='$barcode')"

    internal open fun toProductVariantsBulkInput() =
        ProductVariantsBulkInput(
            barcode = barcode,
            price = price.toPlainString(),
            optionValues = options.map { it.toVariantOptionValueInput() },
            inventoryItem = toInventoryItemInput(),
        )

    protected fun toInventoryItemInput() =
        InventoryItemInput(
            sku = sku,
            tracked = true,
            measurement = toInventoryItemMeasurementInput()
        )

    private fun toInventoryItemMeasurementInput() =
        InventoryItemMeasurementInput(weight.toWeightInput())
}

class ShopifyProductVariant : UnsavedShopifyProductVariant {

    val id: String
    val title: String
    var mediaId: String?

    internal constructor(variant: ProductVariant) : super(
        variant.sku ?: "",
        variant.barcode!!,
        BigDecimal(variant.price),
        variant.selectedOptions.map { ShopifyProductVariantOption(it) },
        variant.inventoryItem.measurement.weight!!
    ) {
        require(variant.media.edges.size <= 1) { "ProductVariant has more media than is supported" }

        id = variant.id
        title = variant.title
        mediaId = variant.media.edges.firstOrNull()?.node?.id
    }

    internal constructor(unsaved: UnsavedShopifyProductVariant, id: String, title: String) : super(
        unsaved.sku,
        unsaved.barcode,
        unsaved.price,
        unsaved.options,
        unsaved.weight
    ) {
        this.id = id
        this.title = title
        mediaId = null
    }

    override fun toString() =
        "ShopifyProductVariant(id='$id', sku='$sku', barcode='$barcode', title='$title')"

    override fun toProductVariantsBulkInput() =
        ProductVariantsBulkInput(
            id = id,
            barcode = barcode,
            price = price.toPlainString(),
            optionValues = options.map { it.toVariantOptionValueInput() },
            inventoryItem = toInventoryItemInput(),
            mediaId = mediaId
        )
}

fun Weight(unit: WeightUnit, value: Double) = Weight.Builder().withUnit(unit).withValue(value).build()

private fun Weight.toWeightInput() = WeightInput(value, unit)