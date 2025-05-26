package de.hinundhergestellt.jhuh.service.ready2order;

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;

import java.util.List;

public class GroupArtooMappedProduct extends ArtooMappedProduct {

    private final ArtooProductGroup group;

    GroupArtooMappedProduct(ArtooProductGroup group, List<ArtooMappedVariation> variations) {
        super(variations);
        this.group = group;
    }

    @Override
    public String getName() {
        return group.getName();
    }
}
