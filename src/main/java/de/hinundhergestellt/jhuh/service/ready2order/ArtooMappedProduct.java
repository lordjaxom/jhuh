package de.hinundhergestellt.jhuh.service.ready2order;

import java.util.List;

public abstract class ArtooMappedProduct {

    private final List<ArtooMappedVariation> variations;

    protected ArtooMappedProduct(List<ArtooMappedVariation> variations) {
        this.variations = variations;
    }

    public abstract String getName();

    public List<ArtooMappedVariation> getVariations() {
        return variations;
    }

    public boolean isReadyForSync() {
        return variations.stream().allMatch(it -> it.getBarcode().isPresent());
    }
}
