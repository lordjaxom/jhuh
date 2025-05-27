package de.hinundhergestellt.jhuh.sync;

import com.shopify.admin.types.ProductVariant;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import de.hinundhergestellt.jhuh.service.ready2order.ArtooDataStore;
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedCategory;
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedProduct;
import de.hinundhergestellt.jhuh.service.ready2order.SingleArtooMappedProduct;
import de.hinundhergestellt.jhuh.service.shopify.ShopifyDataStore;
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static java.util.stream.Stream.concat;

@Service
@VaadinSessionScope
public class ArtooImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtooImportService.class);

    private final ArtooDataStore artooDataStore;
    private final ShopifyDataStore shopifyDataStore;
    private final SyncProductRepository syncProductRepository;
    private final SyncVariantRepository syncVariantRepository;

    public ArtooImportService(ArtooDataStore artooDataStore,
                              ShopifyDataStore shopifyDataStore,
                              SyncProductRepository syncProductRepository,
                              SyncVariantRepository syncVariantRepository) {
        this.artooDataStore = artooDataStore;
        this.shopifyDataStore = shopifyDataStore;
        this.syncProductRepository = syncProductRepository;
        this.syncVariantRepository = syncVariantRepository;
    }

    public List<ArtooMappedCategory> findRootCategories() {
        return artooDataStore.getRootCategories();
    }

    public String getItemName(Object item) {
        return switch (item) {
            case ArtooMappedCategory category -> category.getName();
            case ArtooMappedProduct product -> product.getName();
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
    }

    public Optional<Integer> getItemVariations(Object item) {
        return switch (item) {
            case ArtooMappedCategory ignored -> Optional.empty();
            case SingleArtooMappedProduct ignored -> Optional.of(0);
            case ArtooMappedProduct product -> Optional.of(product.getVariations().size());
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
    }

    public boolean isMarkedForSync(ArtooMappedProduct product) {
        return product.getVariations().stream()
                .flatMap(it -> it.getBarcode().stream())
                .anyMatch(syncVariantRepository::existsByBarcode);
    }

    public boolean filterByReadyToSync(Object item) {
        return switch (item) {
            case ArtooMappedCategory category -> category.containsReadyForSync();
            case ArtooMappedProduct product -> product.isReadyForSync();
            default -> throw new IllegalStateException("Unexpected item " + item);
        };
    }

    public boolean filterByMarkedWithErrors(Object item) {
        return switch (item) {
            case ArtooMappedCategory category ->
                    concat(category.getChildren().stream(), category.getProducts().stream()).anyMatch(this::filterByMarkedWithErrors);
            case ArtooMappedProduct product -> isMarkedForSync(product) && !product.isReadyForSync();
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
            shopifyDataStore.getProducts().forEach(this::reconcileFromShopify);

//            checkAllArtooVariationsSynced(shopifyProducts);
            return completedFuture(null);
        } catch (RuntimeException e) {
            LOGGER.error("Couldn't synchronize with Shopify", e);
            return failedFuture(e);
        }
    }

    private void reconcileFromShopify(ShopifyProduct product) {
        var syncProduct = syncProductRepository
                .findByShopifyId(product.getId())
                .orElseGet(() -> syncProductRepository.save(new SyncProduct(product.getId(), product.getTags())));
        product.getVariants().iterator().forEachRemaining(variant -> reconcileFromShopify(variant, syncProduct));
    }

    private void reconcileFromShopify(ProductVariant variant, SyncProduct syncProduct) {
        var syncVariant = syncVariantRepository
                .findByBarcode(variant.getBarcode())
                .orElseGet(() -> new SyncVariant(syncProduct, variant.getBarcode()));

        Assert.isTrue(syncVariant.getProduct() == syncProduct, "SyncVariant.product does not match ShopifyProduct");
    }

    private void reconcileFromArtoo() {

    }

    private void checkAllArtooVariationsSynced(List<ShopifyProduct> shopifyProducts) {
//        syncProductRepository.findAllBy().forEach(syncProduct -> {
//            var referenceProduct = syncProduct.getVariants().stream()
//                    .flatMap(it -> findProductByBarcode(it.getBarcode()).stream())
//                    .findFirst()
//                    .orElse(null);
//            if (referenceProduct == null) {
//                return;
//            }
//
//            var variationGroup = findVariationGroupByProduct(referenceProduct).orElse(null);
//            var variations = Optional.ofNullable(variationGroup)
//                    .map(this::findProductsByProductGroup)
//                    .map(Stream::toList)
//                    .orElse(List.of(referenceProduct));
//            var productName = Optional.ofNullable(variationGroup)
//                    .map(ArtooProductGroup::getName)
//                    .orElse(referenceProduct.getName());
//
//            // ShopifyProduct not required - data already in sync db
//
//            // TODO: ready2order variations without barcode are ignored - really?
//            syncProduct.getVariants().stream()
//                    .filter(it -> variations.stream().noneMatch(variation ->
//                            variation.getBarcode().map(it.getBarcode()::equals).orElse(false)))
//                    .forEach(it -> {
//                        LOGGER.info("Variant of {} with barcode {} no longer in ready2order, marking for deletion",
//                                productName, it.getBarcode());
//                        syncVariantRepository
//                                .findByBarcode(it.getBarcode())
//                                .ifPresent(sync -> sync.setDeleted(true));
//                    });
//
//            // TODO
//            variations.stream()
//                    .filter(it -> it.getBarcode().isPresent())
//                    .filter(it -> syncProduct.getVariants().stream().noneMatch(variant ->
//                            it.getBarcode().map(variant.getBarcode()::equals).orElse(false)))
//                    .forEach(it -> {
//                        LOGGER.info("Variant {} not in Shopify, adding SyncVariant", it.getName());
//                        syncVariantRepository.save(new SyncVariant(syncProduct, it.getBarcode().orElseThrow()));
//                    });
//        });
//
//        var handledVariationGroups = new HashSet<Integer>();
//        for (var product : products) {
//            var barcode = product.getBarcode().orElse(null);
//            if (barcode == null) {
//                continue;
//            }
//
//            var variationGroup = findVariationGroupByProduct(product).orElse(null);
//            if (variationGroup == null || handledVariationGroups.contains(variationGroup.getId())) {
//                continue;
//            }
//            handledVariationGroups.add(variationGroup.getId());
//
//            var referenceVariant = syncVariantRepository.findByBarcode(barcode).orElse(null);
//            SyncProduct syncProduct;
//            if (referenceVariant == null || (syncProduct = referenceVariant.getProduct()).getShopifyId().isEmpty()) {
//                continue;
//            }
//
//            var artooVariations = findProductsByProductGroup(variationGroup).toList();
//            if (artooVariations.stream().anyMatch(it -> it.getBarcode().isEmpty())) {
//                LOGGER.warn("Not all variations of {} have a barcode in ready2order, skipping", variationGroup.getName());
//                continue;
//            }
//
//            var shopifyProduct = shopifyProducts.stream()
//                    .filter(it -> it.getId().equals(syncProduct.getShopifyId().get()))
//                    .findFirst()
//                    .orElseThrow();
//
//            shopifyProduct.getVariants()
//                    .filter(it -> artooVariations.stream().noneMatch(variation ->
//                            variation.getBarcode().orElseThrow().equals(it.getBarcode())))
//                    .forEach(it -> {
//                        LOGGER.info("Variant {} of {} no longer in ready2order, marking for deletion", it.getTitle(),
//                                shopifyProduct.getTitle());
//                        syncVariantRepository
//                                .findByBarcode(it.getBarcode())
//                                .ifPresent(sync -> sync.setDeleted(true));
//                    });
//
//            artooVariations.stream()
//                    .map(it -> Pair.of(it, it.getBarcode().orElseThrow()))
//                    .filter(it -> shopifyProduct.getVariants().noneMatch(variant -> variant.getBarcode().equals(it.getRight())))
//                    .forEach(it -> {
//                        LOGGER.info("Variant {} not in Shopify, adding SyncVariant", it.getLeft().getName());
//                        syncVariantRepository.save(new SyncVariant(syncProduct, it.getRight()));
//                    });
//        }
    }
}
