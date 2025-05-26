package de.hinundhergestellt.jhuh.sync;

import com.shopify.admin.types.ProductVariant;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductClient;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroupClient;
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct;
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductClient;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.stream.Stream.concat;

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
        productGroups = artooProductGroupClient.findAll().toList();
        products = artooProductClient.findAll().toList();
        this.shopifyProductClient = shopifyProductClient;
        this.syncProductRepository = syncProductRepository;
        this.syncVariantRepository = syncVariantRepository;
    }

    public Optional<ArtooProductGroup> findProductGroupByName(String name) {
        return productGroups.stream().filter(it -> it.getName().equals(name)).findFirst();
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

    public boolean isMarkedForSync(Object item) {
        return switch (item) {
            case ArtooProductGroup group when group.getTypeId() == 3 -> findProductsByProductGroup(group).anyMatch(this::isMarkedForSync);
            case ArtooProduct product -> product.getBarcode().map(syncVariantRepository::existsByBarcode).orElse(false);
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
    }

    public boolean filterByReadyToSync(Object item) {
        return item instanceof ArtooProductGroup group && group.getTypeId() != 3
                ? concat(findProductGroupsByParent(group), findProductsByProductGroup(group)).anyMatch(this::filterByReadyToSync)
                : isReadyForSync(item);
    }

    public boolean filterByMarkedWithErrors(Object item) {
        return item instanceof ArtooProductGroup group && group.getTypeId() != 3
                ? concat(findProductGroupsByParent(group), findProductsByProductGroup(group)).anyMatch(this::filterByMarkedWithErrors)
                : isMarkedForSync(item) && !isReadyForSync(item);
    }

    public void markForSync(Object item) {
    }

    public void unmarkForSync(Object item) {
    }

    @Async
    @Transactional
    public CompletableFuture<Void> syncWithShopify() {
        try {
            var shopifyProducts = shopifyProductClient.findAll().toList();
            shopifyProducts.forEach(this::syncFromShopify);

//            checkAllArtooVariationsSynced(shopifyProducts);
            return completedFuture(null);
        } catch (RuntimeException e) {
            LOGGER.error("Couldn't synchronize with Shopify", e);
            return failedFuture(e);
        }
    }

    private Optional<ArtooProductGroup> findVariationGroupByProduct(ArtooProduct product) {
        return productGroups.stream()
                .filter(it -> it.getId() == product.getProductGroupId() && it.getTypeId() == 3)
                .findFirst();
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

    private void checkAllArtooVariationsSynced(List<ShopifyProduct> shopifyProducts) {
        var handledVariationGroups = new HashSet<Integer>();
        for (var product : products) {
            var barcode = product.getBarcode().orElse(null);
            if (barcode == null) {
                continue;
            }

            var variationGroup = findVariationGroupByProduct(product).orElse(null);
            if (variationGroup == null || handledVariationGroups.contains(variationGroup.getId())) {
                continue;
            }
            handledVariationGroups.add(variationGroup.getId());

            var referenceVariant = syncVariantRepository.findByBarcode(barcode).orElse(null);
            SyncProduct syncProduct;
            if (referenceVariant == null || (syncProduct = referenceVariant.getProduct()).getShopifyId().isEmpty()) {
                continue;
            }

            var artooVariations = findProductsByProductGroup(variationGroup).toList();
            if (artooVariations.stream().anyMatch(it -> it.getBarcode().isEmpty())) {
                LOGGER.warn("Not all variations of {} have a barcode in ready2order, skipping", variationGroup.getName());
                continue;
            }

            var shopifyProduct = shopifyProducts.stream()
                    .filter(it -> it.getId().equals(syncProduct.getShopifyId().get()))
                    .findFirst()
                    .orElseThrow();

            shopifyProduct.getVariants()
                    .filter(it -> artooVariations.stream().noneMatch(variation ->
                            variation.getBarcode().orElseThrow().equals(it.getBarcode())))
                    .forEach(it -> {
                        LOGGER.info("Variant {} of {} no longer in ready2order, marking for deletion", it.getTitle(),
                                shopifyProduct.getTitle());
                        syncVariantRepository
                                .findByBarcode(it.getBarcode())
                                .ifPresent(sync -> sync.setDeleted(true));
                    });

            artooVariations.stream()
                    .map(it -> Pair.of(it, it.getBarcode().orElseThrow()))
                    .filter(it -> shopifyProduct.getVariants().noneMatch(variant -> variant.getBarcode().equals(it.getRight())))
                    .forEach(it -> {
                        LOGGER.info("Variant {} not in Shopify, adding SyncVariant", it.getLeft().getName());
                        syncVariantRepository.save(new SyncVariant(syncProduct, it.getRight()));
                    });
        }
    }
}
