package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.InventoryItemInput
import com.shopify.admin.types.ProductVariant
import com.shopify.admin.types.ProductVariantsBulkInput
import java.math.BigDecimal

open class UnsavedShopifyProductVariant(
    var sku: String,
    var barcode: String,
    var price: BigDecimal,
    val options: List<ShopifyProductVariantOption>
) {
    internal open fun toProductVariantsBulkInput() =
        ProductVariantsBulkInput().also {
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

class ShopifyProductVariant private constructor(
    val id: String,
    val title: String,
    sku: String,
    barcode: String,
    price: BigDecimal,
    options: List<ShopifyProductVariantOption>
) : UnsavedShopifyProductVariant(
    sku,
    barcode,
    price,
    options
) {
    internal constructor(variant: ProductVariant) : this(
        variant.id,
        variant.title,
        variant.sku,
        variant.barcode,
        BigDecimal(variant.price),
        variant.selectedOptions.map { ShopifyProductVariantOption(it) }
    )

    internal constructor(unsaved: UnsavedShopifyProductVariant, id: String, title: String) : this(
        id,
        title,
        unsaved.sku,
        unsaved.barcode,
        unsaved.price,
        unsaved.options
    )

    override fun toProductVariantsBulkInput() =
        super.toProductVariantsBulkInput().also { it.id = id }
}
