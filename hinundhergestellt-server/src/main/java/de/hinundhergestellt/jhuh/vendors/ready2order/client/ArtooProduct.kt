package de.hinundhergestellt.jhuh.vendors.ready2order.client

import de.hinundhergestellt.jhuh.util.DirtyTracker
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
    var variationsEnabled: Boolean = false,
    stockValue: BigDecimal,
    var stockUnit: String? = if (stockEnabled) "piece" else null,
    stockReorderLevel: BigDecimal = BigDecimal.ZERO,
    stockSafetyStock: BigDecimal = BigDecimal.ZERO,
    var sortIndex: Int = 0,
    var active: Boolean,
    var discountable: Boolean = true,
    var type: ArtooProductType = ArtooProductType.INHERITED,
    var baseId: Int? = null,
    var productGroupId: Int,
    var alternativeNameOnReceipts: String = "",
    var alternativeNameInPos: String
) {
    private val dirtyTracker = DirtyTracker()

    var price by dirtyTracker.track(fixedScale(price, 2))
    var vat by fixedScale(vat, 2)
    var stockValue by fixedScale(stockValue, 0)
    var stockReorderLevel by fixedScale(stockReorderLevel, 0)
    var stockSafetyStock by fixedScale(stockSafetyStock, 0)

    override fun toString() =
        "UnsavedArtooProduct(name='$name', description='$description', itemNumber='$itemNumber', barcode='$barcode')"

    internal fun toProductsPostRequest() =
        ProductsPostRequest(
            productName = name,
            productItemnumber = itemNumber,
            productBarcode = barcode,
            productDescription = description,
            productPrice = price.toPlainString(),
            productPriceIncludesVat = priceIncludesVat,
            productActive = active,
            productDiscountable = discountable,
            productVat = vat.toPlainString(),
            productStockEnabled = stockEnabled,
            productVariationsEnabled = variationsEnabled,
            productStockValue = stockValue.toPlainString(),
            productStockReorderLevel = stockReorderLevel.toPlainString(),
            productStockSafetyStock = stockSafetyStock.toPlainString(),
            productStockUnit = stockUnit,
            productSortIndex = sortIndex,
            productType = type.type,
            productBase = toProductsPostRequestProductBase(),
            productgroup = toProductsPostRequestProductgroup(),
            productAlternativeNameOnReceipts = alternativeNameOnReceipts,
            productAlternativeNameInPos = alternativeNameInPos,
        )

    protected fun toProductsPostRequestProductBase() =
        ProductsPostRequestProductBase(baseId)

    protected fun toProductsPostRequestProductgroup() =
        ProductsPostRequestProductgroup(productGroupId)
}

class ArtooProduct : UnsavedArtooProduct {

    val id: Int

    internal constructor(product: ProductsGet200ResponseInner) : super(
        product.productName!!,
        product.productItemnumber,
        product.productBarcode,
        product.productDescription!!,
        BigDecimal(product.productPrice),
        product.productPriceIncludesVat!!,
        BigDecimal(product.productVat),
        product.productStockEnabled!!,
        product.productVariationsEnabled!!,
        BigDecimal(product.productStockValue),
        product.productStockUnit,
        product.productStockReorderLevel?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
        product.productStockSafetyStock?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
        product.productSortIndex!!,
        product.productActive!!,
        product.productDiscountable!!,
        ArtooProductType.valueOf(product.productTypeId),
        product.productBaseId,
        product.productgroupId ?: product.productgroup!!.productgroupId!!,
        product.productAlternativeNameOnReceipts ?: "",
        product.productAlternativeNameInPos ?: ""
    ) {
        id = product.productId!!
    }

    override fun toString() =
        "ArtooProduct(id=$id, name='$name', description='$description', itemNumber='$itemNumber', barcode='$barcode')"

    internal fun toProductsIdPutRequest() =
        ProductsIdPutRequest(
            productName = name,
            productItemnumber = itemNumber,
            productBarcode = barcode,
            productDescription = description,
            productPrice = price.toPlainString(),
            productPriceIncludesVat = priceIncludesVat,
            productActive = active,
            productDiscountable = discountable,
            productVat = vat.toPlainString(),
            productStockEnabled = stockEnabled,
            productVariationsEnabled = variationsEnabled,
            productStockValue = stockValue.toPlainString(),
            productStockReorderLevel = stockReorderLevel.toPlainString(),
            productStockSafetyStock = stockSafetyStock.toPlainString(),
            productStockUnit = stockUnit,
            productSortIndex = sortIndex,
            productType = type.type,
            productBase = toProductsPostRequestProductBase(),
            productgroup = toProductsPostRequestProductgroup(),
            productAlternativeNameOnReceipts = alternativeNameOnReceipts,
            productAlternativeNameInPos = alternativeNameInPos,
        )
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