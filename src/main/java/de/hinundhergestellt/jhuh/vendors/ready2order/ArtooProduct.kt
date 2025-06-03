package de.hinundhergestellt.jhuh.vendors.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.api.ProductApi
import de.hinundhergestellt.jhuh.vendors.ready2order.model.ProductsGet200ResponseInner
import de.hinundhergestellt.jhuh.vendors.ready2order.model.ProductsIdPutRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.model.ProductsPostRequest
import de.hinundhergestellt.jhuh.vendors.ready2order.model.ProductsPostRequestProductBase
import de.hinundhergestellt.jhuh.vendors.ready2order.model.ProductsPostRequestProductgroup
import java.math.BigDecimal

class ArtooProduct {

    private var value: ProductsGet200ResponseInner

    constructor(
        name: String,
        itemNumber: String?,
        barcode: String?,
        description: String?,
        price: BigDecimal,
        priceIncludesVat: Boolean,
        vat: BigDecimal?,
        stockEnabled: Boolean,
        variationsEnabled: Boolean,
        stockValue: BigDecimal,
        stockUnit: String?,
        stockReorderLevel: BigDecimal,
        stockSafetyStock: BigDecimal,
        sortIndex: Int,
        active: Boolean,
        discountable: Boolean,
        type: String?,
        baseId: Int?,
        productGroupId: Int
    ) {
        value = ProductsGet200ResponseInner()
        value.productName = name
        // value.setProductExternalReference();
        value.productItemnumber = itemNumber
        value.productBarcode = barcode
        value.productDescription = description
        value.productPrice = price.toPlainString()
        value.productPriceIncludesVat = priceIncludesVat
        value.productVat = vat?.toPlainString()
        //        value.setProductCustomPrice();
//        value.setProductCustomQuantity();
//        value.setProductFav();
//        value.setProductHighlight();
//        value.setProductExpressMode();
        value.productStockEnabled = stockEnabled
        //        value.setProductIngredientsEnabled();
        value.productVariationsEnabled = variationsEnabled
        value.productStockValue = stockValue.toPlainString()
        value.productStockUnit = stockUnit
        value.productStockReorderLevel = stockReorderLevel.toPlainString()
        value.productStockSafetyStock = stockSafetyStock.toPlainString()
        value.productSortIndex = sortIndex
        value.productActive = active
        //        value.setProductSoldOut();
//        value.setProductSideDishOrder();
        value.productDiscountable = discountable
        //        value.setProductAccountingCode();
//        value.setProductColorClass();
//        value.setProductTypeId(typeId);
        value.productType = type
        //        value.setProductCreatedAt();
//        value.setProductUpdatedAt();
//        value.setProductAlternativeNameOnReceipts();
//        value.setProductAlternativeNameInPos();
        value.productBaseId = baseId
        value.productgroupId = productGroupId
    }

    internal constructor(value: ProductsGet200ResponseInner) {
        this.value = value
        fixUp()
    }

    val id: Int
        get() = value.productId!!

    val name: String
        get() = value.productName!!

    val description: String
        get() = value.productDescription!!

    val itemNumber: String?
        get() = value.productItemnumber

    val barcode: String?
        get() = value.productBarcode

    val price: BigDecimal
        get() = BigDecimal(value.productPrice!!).setScale(2)

    val stockValue: BigDecimal
        get() = BigDecimal(value.productStockValue!!).setScale(0)

    val typeId: Int
        get() = value.productTypeId ?: 0

    val productGroupId: Int
        get() = value.productgroupId!!

    val baseId: Int
        get() = value.productBaseId ?: 0

    fun save(api: ProductApi) {
        if (value.productId != null) {
            value = api.productsIdPut(value.productId, toPutRequest())
        } else {
            value = api.productsPost(toPostRequest())
        }
    }

    override fun toString(): String {
        return value.toString()
    }

    private fun fixUp() {
        value.productgroupId = value.productgroup!!.productgroupId!!
    }

    private fun toPutRequest(): ProductsIdPutRequest {
        val request = ProductsIdPutRequest()
        request.productName = value.productName
        request.productItemnumber = value.productItemnumber
        request.productExternalReference = value.productExternalReference
        request.productBarcode = value.productBarcode
        request.productDescription = value.productDescription
        request.productPrice = value.productPrice
        request.productPriceIncludesVat = value.productPriceIncludesVat
        request.productActive = value.productActive
        request.productDiscountable = value.productDiscountable
        request.productVat = value.productVat
        request.productStockEnabled = value.productStockEnabled
        request.productStockValue = value.productStockValue
        request.productStockReorderLevel = value.productStockReorderLevel ?: "0"
        request.productStockSafetyStock = value.productStockSafetyStock ?: "0"
        request.productStockUnit = value.productStockUnit
        request.productSortIndex = value.productSortIndex
        request.productType = value.productType
        // ProductBase cannot be modified
        request.productgroup = toProductgroup()
        request.productVariationsEnabled = value.productVariationsEnabled
        return request
    }

    private fun toPostRequest(): ProductsPostRequest {
        val request = ProductsPostRequest()
        request.productName = value.productName
        request.productPrice = value.productPrice
        request.productVat = value.productVat
        request.productItemnumber = value.productItemnumber
        request.productExternalReference = value.productExternalReference
        request.productBarcode = value.productBarcode
        request.productDescription = value.productDescription
        request.productPriceIncludesVat = value.productPriceIncludesVat
        request.productActive = value.productActive
        request.productDiscountable = value.productDiscountable
        request.productStockEnabled = value.productStockEnabled
        request.productStockValue = value.productStockValue
        request.productStockReorderLevel = value.productStockReorderLevel
        request.productStockSafetyStock = value.productStockSafetyStock
        request.productStockUnit = value.productStockUnit
        request.productSortIndex = value.productSortIndex
        request.productType = value.productType
        request.productBase = toProductBase()
        request.productgroup = toProductgroup()
        request.productVariationsEnabled = value.productVariationsEnabled
        return request
    }

    private fun toProductBase(): ProductsPostRequestProductBase? =
        value.productBaseId?.let { baseId -> ProductsPostRequestProductBase().apply { productId = baseId } }

    private fun toProductgroup(): ProductsPostRequestProductgroup {
        val productgroup = ProductsPostRequestProductgroup()
        productgroup.productgroupId = value.productgroupId
        return productgroup
    }
}