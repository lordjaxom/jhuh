package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.*
import java.math.BigDecimal

class ShopifyProductVariant private constructor(
    var id: String?,
    var title: String?,
    var sku: String,
    var barcode: String,
    var price: BigDecimal,
    val options: List<ShopifyProductVariantOption>
) {
    constructor(
        sku: String,
        barcode: String,
        price: BigDecimal,
        options: List<ShopifyProductVariantOption>
    ) : this(
        null,
        null,
        sku,
        barcode,
        price,
        options
    )

    internal constructor(variant: ProductVariant) : this(
        variant.id,
        variant.title,
        variant.sku,
        variant.barcode,
        BigDecimal(variant.price),
        variant.selectedOptions.map { ShopifyProductVariantOption(it) }
    )

    internal fun toProductVariantsBulkInput() =
        ProductVariantsBulkInput().also {
            it.id = id
            it.barcode = barcode
            it.price = price.toPlainString()
            it.optionValues = options.map { option -> option.toVariantOptionValueInput() }
            it.inventoryItem = toInventoryItemInput()
        }

    private fun toInventoryItemInput() =
        InventoryItemInput().also {
            it.sku = sku
            it.tracked = true
        }
}
