package de.hinundhergestellt.jhuh.sync;

import com.shopify.admin.types.ProductVariant;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductClient;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroupClient;
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct;
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;

@Service
@VaadinSessionScope
public class ArtooImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtooImportService.class);

    private final List<ArtooProductGroup> productGroups;
    private final List<ArtooProduct> products;
    private final ShopifyProductClient shopifyProductClient;
    private final SyncProductRepository syncProductRepository;
    private final SyncVariantRepository syncVariantRepository;

    public ArtooImportService(ArtooProductGroupClient artooProductGroupClient,
                              ArtooProductClient artooProductClient,
                              ShopifyProductClient shopifyProductClient,
                              SyncProductRepository syncProductRepository,
                              SyncVariantRepository syncVariantRepository) {
        productGroups = artooProductGroupClient.findAll();
        products = artooProductClient.findAll();
        this.shopifyProductClient = shopifyProductClient;
        this.syncProductRepository = syncProductRepository;
        this.syncVariantRepository = syncVariantRepository;
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
            case ArtooProductGroup group when group.getTypeId() == 3 -> findProductsByProductGroup(group).allMatch(this::isReadyForSync);
            case ArtooProduct product -> product.getBarcode().isPresent();
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
    }

    public boolean isOrContainsReadyForSync(Object item) {
        return item instanceof ArtooProductGroup group && group.getTypeId() != 3
                ? containsReadyForSync(group)
                : isReadyForSync(item);
    }

    public boolean isMarkedForSync(Object item) {
        return switch (item) {
            case ArtooProductGroup group when group.getTypeId() == 3 -> findProductsByProductGroup(group).anyMatch(this::isMarkedForSync);
            case ArtooProduct product -> product.getBarcode().map(syncVariantRepository::existsByBarcode).orElse(false);
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
    }

    public void markForSync(Object item) {
    }

    public void unmarkForSync(Object item) {
    }

    @Async
    @Transactional
    public CompletableFuture<Void> syncWithShopify() {
        try {
            shopifyProductClient.findAll().forEach(this::syncFromShopify);



            return completedFuture(null);
        } catch (RuntimeException e) {
            LOGGER.error("Couldn't synchronize with Shopify", e);
            return failedFuture(e);
        }
    }

    private boolean containsReadyForSync(ArtooProductGroup group) {
        return findProductGroupsByParent(group).anyMatch(this::containsReadyForSync) ||
                findProductsByProductGroup(group).anyMatch(this::isReadyForSync);
    }

    private void syncFromShopify(ShopifyProduct product) {
        var syncProduct = syncProductRepository
                .findByShopifyId(product.getId())
                .orElseGet(() -> syncProductRepository.save(new SyncProduct(product.getId(), product.getTags())));
        product.getVariants().forEach(variant -> syncFromShopify(variant, syncProduct));
    }

    private void syncFromShopify(ProductVariant variant, SyncProduct syncProduct) {
        var syncVariant = syncVariantRepository
                .findByBarcode(variant.getBarcode())
                .orElseGet(() -> new SyncVariant(syncProduct, variant.getBarcode()));

        Assert.isTrue(syncVariant.getProduct() == syncProduct, "SyncVariant.product does not match ShopifyProduct");
    }
}
