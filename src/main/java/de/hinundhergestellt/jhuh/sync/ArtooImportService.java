package de.hinundhergestellt.jhuh.sync;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductClient;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroupClient;
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;

@Service
@VaadinSessionScope
public class ArtooImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtooImportService.class);

    private final List<ArtooProductGroup> productGroups;
    private final List<ArtooProduct> products;
    private final ShopifyProductClient shopifyProductClient;
    private final SyncProductRepository repository;

    public ArtooImportService(ArtooProductGroupClient artooProductGroupClient,
                              ArtooProductClient artooProductClient,
                              ShopifyProductClient shopifyProductClient,
                              SyncProductRepository repository) {
        productGroups = artooProductGroupClient.findAll();
        products = artooProductClient.findAll();
        this.shopifyProductClient = shopifyProductClient;
        this.repository = repository;
    }

    public Stream<ArtooProductGroup> findRootProductGroups() {
        return productGroups.stream().filter(it -> it.getParent() == 0 && it.getTypeId() != 0);
    }

    public Stream<ArtooProductGroup> findProductGroupsByParent(ArtooProductGroup parent) {
        return productGroups.stream().filter(it -> it.getParent() == parent.getId());
    }

    public Stream<ArtooProduct> findProductsByProductGroup(ArtooProductGroup productGroup) {
        return products.stream().filter(it -> it.getProductGroupId() == productGroup.getId());
    }

    public String getItemName(Object item) {
        return switch (item) {
            case ArtooProductGroup group -> group.getName();
            case ArtooProduct product -> product.getName();
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
    }

    public Optional<Long> getItemVariations(Object item) {
        return switch (item) {
            case ArtooProductGroup group when group.getTypeId() == 3 -> Optional.of(findProductsByProductGroup(group).count());
            case ArtooProductGroup ignored -> Optional.empty();
            default -> Optional.of(0L);
        };
    }

    public boolean isSyncable(Object item) {
        return switch (item) {
            case ArtooProductGroup group when group.getTypeId() == 3 -> true;
            case ArtooProduct ignored -> true;
            default -> false;
        };
    }

    public boolean isReadyForSync(Object item) {
        return switch (item) {
            case ArtooProductGroup group when group.getTypeId() == 3 ->
                    findProductsByProductGroup(group).allMatch(it -> it.getBarcode().isPresent());
            case ArtooProduct product -> product.getBarcode().isPresent();
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
    }

    public boolean isMarkedForSync(Object item) {
        return false;
    }

    public void markForSync(Object item) {

    }

    public void unmarkForSync(Object item) {

    }

    @Async
    public CompletableFuture<Void> syncWithShopify() {
        return completedFuture(null);
    }
}
