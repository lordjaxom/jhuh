package de.hinundhergestellt.jhuh.ready2order;

import de.hinundhergestellt.jhuh.ready2order.model.ProductsGet200ResponseInner;

import java.util.List;

public class Product extends ProductVariation {

    private List<ProductVariation> variations;

    Product(ProductsGet200ResponseInner value, List<ProductVariation> variations) {
        super(value);
        this.variations = variations;
    }
}
