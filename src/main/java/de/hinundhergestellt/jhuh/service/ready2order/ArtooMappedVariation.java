package de.hinundhergestellt.jhuh.service.ready2order;

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct;

import java.util.Optional;

public class ArtooMappedVariation {

    private final ArtooProduct product;

    ArtooMappedVariation(ArtooProduct product) {
        this.product = product;
    }

    public Optional<String> getBarcode() {
        return product.getBarcode();
    }
}
