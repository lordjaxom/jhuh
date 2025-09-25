package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.core.fixedScale
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.InventoryItemInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.InventoryItemMeasurementInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.InventoryLevelInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Weight
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.WeightUnit
import java.math.BigDecimal

class ShopifyProductVariant private constructor(
    internal var internalId: String?,
    internal var internalTitle: String?,
    var sku: String,
    var barcode: String,
    price: BigDecimal,
    weight: BigDecimal,
    val inventoryQuantity: Int, // must not be mutated after creation
    var mediaId: String?,
    val options: List<ShopifyProductOptionValue>
) {
    val id get() = internalId!!
    val title get() = internalTitle ?: options.variantTitle
    var price by fixedScale(price, 2)
    var weight by fixedScale(weight, 2)

    constructor(
        sku: String,
        barcode: String,
        price: BigDecimal,
        weight: BigDecimal,
        inventoryQuantity: Int,
        options: List<ShopifyProductOptionValue>
    ) : this(
        internalId = null,
        internalTitle = null,
        sku = sku,
        barcode = barcode,
        price = price,
        weight = weight,
        inventoryQuantity = inventoryQuantity,
        mediaId = null,
        options = options
    )

    internal constructor(variant: ProductVariant) : this(
        variant.id,
        variant.title,
        variant.sku ?: "",
        variant.barcode!!,
        BigDecimal(variant.price),
        variant.inventoryItem.measurement.weight!!.toGrams(),
        variant.inventoryQuantity!!,
        variant.media.edges.firstOrNull()?.node?.id,
        variant.selectedOptions.map { ShopifyProductOptionValue(it) }
    )

    override fun toString() =
        "ShopifyProductVariant(id='$internalId', title='$title', sku='$sku', barcode='$barcode', price=$price)"

    internal fun toProductVariantsCreateBulkInput(inventoryLocationId: String): ProductVariantsBulkInput {
        require(internalId == null) { "Cannot recreate existing product variant" }
        return ProductVariantsBulkInput(
            barcode = barcode,
            price = price.toPlainString(),
            optionValues = options.map { it.toVariantOptionValueInput() },
            inventoryItem = toInventoryItemInput(),
            inventoryQuantities = listOf(
                InventoryLevelInput(
                    locationId = inventoryLocationId,
                    availableQuantity = inventoryQuantity
                )
            ),
            mediaId = mediaId
        )
    }

    internal fun toProductVariantsUpdateBulkInput() =
        ProductVariantsBulkInput(
            id = id,
            barcode = barcode,
            price = price.toPlainString(),
            optionValues = options.map { it.toVariantOptionValueInput() },
            inventoryItem = toInventoryItemInput(),
            mediaId = mediaId
        )

    private fun toInventoryItemInput() =
        InventoryItemInput(
            sku = sku,
            tracked = true,
            measurement = InventoryItemMeasurementInput(
                weight = WeightInput(weight, WeightUnit.GRAMS)
            )
        )
}

private fun Weight.toGrams() = when (unit) {
    WeightUnit.KILOGRAMS -> value.multiply(BigDecimal(1000))
    WeightUnit.GRAMS -> value
    WeightUnit.POUNDS -> value.multiply(BigDecimal("453.592"))
    WeightUnit.OUNCES -> value.multiply(BigDecimal("28.3495"))
}
