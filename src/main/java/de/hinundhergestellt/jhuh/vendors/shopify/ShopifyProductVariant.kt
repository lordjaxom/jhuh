package de.hinundhergestellt.jhuh.vendors.shopify

import com.shopify.admin.types.*
import java.math.BigDecimal

class ShopifyProductVariant internal constructor(
    private val variant: ProductVariant
) {
    var id: String? by variant::id
    var title: String by variant::title
    var barcode: String by variant::barcode

    var price: BigDecimal
        get() = BigDecimal(variant.price)
        set(value) {
            variant.price = value.toPlainString()
        }

    constructor(
        title: String,
        sku: String?,
        barcode: String,
        price: BigDecimal,
        selectedOption: SelectedOption
    ) : this(ProductVariant().also {
        it.title = title
        it.sku = sku
        it.barcode = barcode
        it.price = price.toPlainString()
        it.selectedOptions = listOf(selectedOption)
    })

    internal fun toProductVariantsBulkInput() = ProductVariantsBulkInput().apply {
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
        }
    }
}
