package de.hinundhergestellt.jhuh.service.ready2order;

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import org.springframework.util.Assert;

import java.util.List;

public class ArtooMappedCategory {

    private final ArtooProductGroup group;
    private final List<ArtooMappedCategory> children;
    private final List<ArtooMappedProduct> products;

    ArtooMappedCategory(ArtooProductGroup group, List<ArtooMappedCategory> children, List<ArtooMappedProduct> products) {
        this.group = group;
        this.children = children;
        this.products = products;
    }

    public String getName() {
        return group.getName();
    }

    public List<ArtooMappedCategory> getChildren() {
        return children;
    }

    public List<ArtooMappedProduct> getProducts() {
        return products;
    }

    public boolean containsReadyForSync() {
        return children.stream().anyMatch(ArtooMappedCategory::containsReadyForSync) ||
                products.stream().anyMatch(ArtooMappedProduct::isReadyForSync);
    }
}
