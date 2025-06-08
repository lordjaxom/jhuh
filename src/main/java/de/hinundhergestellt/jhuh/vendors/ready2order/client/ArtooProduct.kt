package de.hinundhergestellt.jhuh.vendors.ready2order.client

import de.hinundhergestellt.jhuh.util.fixedScale
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsGet200ResponseInner
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsIdPutRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductBase
import de.hinundhergestellt.jhuh.vendors.ready2order.openapi.model.ProductsPostRequestProductgroup
import java.math.BigDecimal

open class UnsavedArtooProduct(
    var name: String,
    var itemNumber: String?,
    var barcode: String?,
    var description: String,
    price: BigDecimal,
    var priceIncludesVat: Boolean,
    vat: BigDecimal,
    var stockEnabled: Boolean,
    var variationsEnabled: Boolean,
    stockValue: BigDecimal,
    var stockUnit: String? = if (stockEnabled) "piece" else null,
    stockReorderLevel: BigDecimal,
    stockSafetyStock: BigDecimal,
    var sortIndex: Int = 0,
    var active: Boolean,
    var discountable: Boolean,
    var type: ArtooProductType = ArtooProductType.INHERITED,
    var baseId: Int? = null,
    var productGroupId: Int,
    var alternativeNameOnReceipts: String,
    var alternativeNameInPos: String
) {
    var price by fixedScale(price, 2)
    var vat by fixedScale(vat, 2)
    var stockValue by fixedScale(stockValue, 0)
    var stockReorderLevel by fixedScale(stockReorderLevel, 0)
    var stockSafetyStock by fixedScale(stockSafetyStock, 0)

    override fun toString() =
        "UnsavedArtooProduct(name='$name', description='$description', itemNumber='$itemNumber', barcode='$barcode')"

    internal fun toProductsPostRequest() =
        ProductsPostRequest().also {
            it.productName = name
            it.productItemnumber = itemNumber
            it.productBarcode = barcode
            it.productDescription = description
            it.productPrice = price.toPlainString()
            it.productPriceIncludesVat = priceIncludesVat
            it.productActive = active
            it.productDiscountable = discountable
            it.productVat = vat.toPlainString()
            it.productStockEnabled = stockEnabled
            it.productVariationsEnabled = variationsEnabled
            it.productStockValue = stockValue.toPlainString()
            it.productStockReorderLevel = stockReorderLevel.toPlainString()
            it.productStockSafetyStock = stockSafetyStock.toPlainString()
            it.productStockUnit = stockUnit
            it.productSortIndex = sortIndex
            it.productType = type.type
            it.productBase = toProductsPostRequestProductBase()
            it.productgroup = toProductsPostRequestProductgroup()
            it.productAlternativeNameOnReceipts = alternativeNameOnReceipts
            it.productAlternativeNameInPos = alternativeNameInPos
        }

    protected fun toProductsPostRequestProductBase() =
        ProductsPostRequestProductBase().also {
            it.productId = baseId
        }

    protected fun toProductsPostRequestProductgroup() =
        ProductsPostRequestProductgroup().also {
            it.productgroupId = productGroupId
        }
}

class ArtooProduct : UnsavedArtooProduct {

    val id: Int
    val typeId: Int?

    internal constructor(product: ProductsGet200ResponseInner) : super(
        product.productName,
        product.productItemnumber,
        product.productBarcode,
        product.productDescription,
        BigDecimal(product.productPrice),
        product.productPriceIncludesVat,
        BigDecimal(product.productVat),
        product.productStockEnabled,
        product.productVariationsEnabled,
        BigDecimal(product.productStockValue),
        product.productStockUnit,
        product.productStockReorderLevel?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
        product.productStockSafetyStock?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
        product.productSortIndex,
        product.productActive,
        product.productDiscountable,
        ArtooProductType.valueOf(product.productTypeId),
        product.productBaseId,
        product.productgroup!!.productgroupId,
        product.productAlternativeNameOnReceipts ?: "",
        product.productAlternativeNameInPos ?: ""
    ) {
        id = product.productId
        typeId = product.productTypeId
    }

    override fun toString() =
        "ArtooProduct(id=$id, name='$name', description='$description', itemNumber='$itemNumber', barcode='$barcode')"

    internal fun toProductsIdPutRequest() =
        ProductsIdPutRequest().also {
            it.productName = name
            it.productItemnumber = itemNumber
            it.productBarcode = barcode
            it.productDescription = description
            it.productPrice = price.toPlainString()
            it.productPriceIncludesVat = priceIncludesVat
            it.productActive = active
            it.productDiscountable = discountable
            it.productVat = vat.toPlainString()
            it.productStockEnabled = stockEnabled
            it.productVariationsEnabled = variationsEnabled
            it.productStockValue = stockValue.toPlainString()
            it.productStockReorderLevel = stockReorderLevel.toPlainString()
            it.productStockSafetyStock = stockSafetyStock.toPlainString()
            it.productStockUnit = stockUnit
            it.productSortIndex = sortIndex
            it.productType = type.type
            it.productBase = toProductsPostRequestProductBase()
            it.productgroup = toProductsPostRequestProductgroup()
            it.productAlternativeNameOnReceipts = alternativeNameOnReceipts
            it.productAlternativeNameInPos = alternativeNameInPos
        }
}

enum class ArtooProductType(
    val id: Int?,
    val type: String?
) {
    INHERITED(null, null),
    VARIATION(5, "variation"),
    STANDARD(7, "standard");

    companion object {
        fun valueOf(id: Int?) = entries.find { it.id == id }!!
    }
}