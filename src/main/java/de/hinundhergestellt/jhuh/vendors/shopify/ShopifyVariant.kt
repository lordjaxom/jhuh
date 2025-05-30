package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.*
import java.math.BigDecimal

class ShopifyVariant internal constructor(
    private val variant: ProductVariant
) {
    constructor(
        title: String,
        sku: String?,
        barcode: String,
        price: BigDecimal?,
        selectedOption: SelectedOption
    ) : this(ProductVariant().also {
        it.title = title
        it.sku = sku
        it.barcode = barcode
        it.price = price?.toPlainString()
        it.selectedOptions = listOf(selectedOption)
    })

    val id: String? by variant::id
    val title: String? by variant::title
    val sku: String? by variant::sku
    val barcode: String by variant::barcode

    val price: BigDecimal?
        get() = variant.price?.let { BigDecimal(it) }

    internal fun toEdge() = ProductVariantEdge().apply { node = variant }

    internal fun toProductVariantsBulkInput() =
        ProductVariantsBulkInput().apply {
            id = variant.id
            barcode = variant.barcode

            optionValues = variant.selectedOptions.map {
                VariantOptionValueInput().apply {
                    optionName = it.name
                    name = it.value
                }
            }
            inventoryItem = InventoryItemInput().apply {
                sku = variant.sku
                price = variant.price
                tracked = true
//                inventoryQuantities = listOf(InventoryLevelInput().apply {
//                    availableQuantity = variant.inventoryQuantity
//                })
            }
        }
}
