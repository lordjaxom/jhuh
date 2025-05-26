package de.hinundhergestellt.jhuh.service.ready2order;

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct;

import java.util.List;

public class SingleArtooMappedProduct extends ArtooMappedProduct {

    private final ArtooProduct product;

    SingleArtooMappedProduct(ArtooProduct product) {
        super(List.of(new ArtooMappedVariation(product)));
        this.product = product;
    }

    @Override
    public String getName() {
        return product.getName();
    }
}
