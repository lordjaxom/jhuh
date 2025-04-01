package de.hinundhergestellt.jhuh.ready2order;

import de.hinundhergestellt.jhuh.ready2order.api.ProductApi;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsGet200ResponseInner;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsGet200ResponseInnerProductgroup;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsIdPutRequest;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsPostRequest;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsPostRequestProductBase;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsPostRequestProductgroup;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.Optional;

import static de.hinundhergestellt.jhuh.ready2order.ApiUtils.withDefault;
import static java.util.Objects.requireNonNull;

public class ProductVariation {

    private ProductsGet200ResponseInner value;

    public ProductVariation(String name, @Nullable String itemNumber, @Nullable String barcode, @Nullable String description,
                            BigDecimal price, boolean priceIncludesVat, @Nullable BigDecimal vat, boolean stockEnabled,
                            boolean variationsEnabled, BigDecimal stockValue, @Nullable String stockUnit, BigDecimal stockReorderLevel,
                            BigDecimal stockSafetyStock, int sortIndex, boolean active, boolean discountable, @Nullable Integer typeId,
                            @Nullable Integer baseId, int productGroupId) {
        value = new ProductsGet200ResponseInner();
        value.setProductName(name);
        // value.setProductExternalReference();
        value.setProductItemnumber(itemNumber);
        value.setProductBarcode(barcode);
        value.setProductDescription(description);
        value.setProductPrice(price.toPlainString());
        value.setProductPriceIncludesVat(priceIncludesVat);
        value.setProductVat(Optional.ofNullable(vat).map(BigDecimal::toPlainString).orElse(null));
//        value.setProductCustomPrice();
//        value.setProductCustomQuantity();
//        value.setProductFav();
//        value.setProductHighlight();
//        value.setProductExpressMode();
        value.setProductStockEnabled(stockEnabled);
//        value.setProductIngredientsEnabled();
        value.setProductVariationsEnabled(variationsEnabled);
        value.setProductStockValue(stockValue.toPlainString());
        value.setProductStockUnit(stockUnit);
        value.setProductStockReorderLevel(stockReorderLevel.toPlainString());
        value.setProductStockSafetyStock(stockSafetyStock.toPlainString());
        value.setProductSortIndex(sortIndex);
        value.setProductActive(active);
//        value.setProductSoldOut();
//        value.setProductSideDishOrder();
        value.setProductDiscountable(discountable);
//        value.setProductAccountingCode();
//        value.setProductColorClass();
        value.setProductTypeId(typeId);
//        value.setProductType();
//        value.setProductCreatedAt();
//        value.setProductUpdatedAt();
//        value.setProductAlternativeNameOnReceipts();
//        value.setProductAlternativeNameInPos();
        value.setProductBaseId(baseId);
        value.setProductgroupId(productGroupId);
    }

    ProductVariation(ProductsGet200ResponseInner value) {
        this.value = value;
        fixUp();
    }

    public int getId() {
        return requireNonNull(value.getProductId());
    }

    public String getName() {
        return requireNonNull(value.getProductName());
    }

    public Optional<String> getItemNumber() {
        return Optional.ofNullable(value.getProductItemnumber());
    }

    public void setItemNumber(@Nullable String itemNumber) {
        value.setProductItemnumber(itemNumber);
    }

    void save(ProductApi api) {
        if (value.getProductId() != null) {
            value = api.productsIdPut(value.getProductId(), toPutRequest());
        } else {
            value = api.productsPost(toPostRequest());
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }

    private void fixUp() {
        value.setProductgroupId(requireNonNull(value.getProductgroup()).getProductgroupId());
    }

    private ProductsIdPutRequest toPutRequest() {
        var request = new ProductsIdPutRequest();
        request.setProductName(value.getProductName());
        request.setProductItemnumber(value.getProductItemnumber());
        request.setProductExternalReference(value.getProductExternalReference());
        request.setProductBarcode(value.getProductBarcode());
        request.setProductDescription(value.getProductDescription());
        request.setProductPrice(value.getProductPrice());
        request.setProductPriceIncludesVat(value.getProductPriceIncludesVat());
        request.setProductActive(value.getProductActive());
        request.setProductDiscountable(value.getProductDiscountable());
        request.setProductVat(value.getProductVat());
        request.setProductStockEnabled(value.getProductStockEnabled());
        request.setProductStockValue(value.getProductStockValue());
        request.setProductStockReorderLevel(withDefault(value.getProductStockReorderLevel(), "0"));
        request.setProductStockSafetyStock(withDefault(value.getProductStockSafetyStock(), "0"));
        request.setProductStockUnit(value.getProductStockUnit());
        request.setProductSortIndex(value.getProductSortIndex());
        request.setProductType(value.getProductType());
        // ProductBase cannot be modified
        request.setProductgroup(toProductgroup());
        request.setProductVariationsEnabled(value.getProductVariationsEnabled());
        return request;
    }

    private ProductsPostRequest toPostRequest() {
        var request = new ProductsPostRequest();
        request.setProductName(value.getProductName());
        request.setProductPrice(value.getProductPrice());
        request.setProductVat(value.getProductVat());
        request.setProductItemnumber(value.getProductItemnumber());
        request.setProductExternalReference(value.getProductExternalReference());
        request.setProductBarcode(value.getProductBarcode());
        request.setProductDescription(value.getProductDescription());
        request.setProductPriceIncludesVat(value.getProductPriceIncludesVat());
        request.setProductActive(value.getProductActive());
        request.setProductDiscountable(value.getProductDiscountable());
        request.setProductStockEnabled(value.getProductStockEnabled());
        request.setProductStockValue(value.getProductStockValue());
        request.setProductStockReorderLevel(value.getProductStockReorderLevel());
        request.setProductStockSafetyStock(value.getProductStockSafetyStock());
        request.setProductStockUnit(value.getProductStockUnit());
        request.setProductSortIndex(value.getProductSortIndex());
        request.setProductType(value.getProductType());
        request.setProductBase(toProductBase());
        request.setProductgroup(toProductgroup());
        request.setProductVariationsEnabled(value.getProductVariationsEnabled());
        return request;
    }

    private @Nullable ProductsPostRequestProductBase toProductBase() {
        return Optional.ofNullable(value.getProductBaseId())
                .map(it -> {
                    var productBase = new ProductsPostRequestProductBase();
                    productBase.setProductId(it);
                    return productBase;
                })
                .orElse(null);
    }

    private ProductsPostRequestProductgroup toProductgroup() {
        var productgroup = new ProductsPostRequestProductgroup();
        productgroup.setProductgroupId(value.getProductgroupId());
        return productgroup;
    }
}