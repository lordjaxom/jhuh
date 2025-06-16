package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.util.fixedScale
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.InventoryItemInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariant
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductVariantsBulkInput
import java.math.BigDecimal

open class UnsavedShopifyProductVariant(
    var sku: String,
    var barcode: String,
    price: BigDecimal,
    val options: List<ShopifyProductVariantOption>
) {
    var price by fixedScale(price, 2)

    override fun toString() =
        "UnsavedShopifyProductVariant(sku='$sku', barcode='$barcode')"

    internal open fun toProductVariantsBulkInput() =
        toProductVariantsBulkInput(null)

    protected fun toProductVariantsBulkInput(id: String?) =
        ProductVariantsBulkInput(
            id = id,
            barcode = barcode,
            price = price.toPlainString(),
            optionValues = options.map { it.toVariantOptionValueInput() },
            inventoryItem = toInventoryItemInput()
        )

    private fun toInventoryItemInput() =
        InventoryItemInput(
            sku = sku,
            tracked = true
        )
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
        variant.sku ?: "",
        variant.barcode!!,
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

    override fun toString() =
        "ShopifyProductVariant(id='$id', sku='$sku', barcode='$barcode')"

    override fun toProductVariantsBulkInput() =
        super.toProductVariantsBulkInput(id)
}
