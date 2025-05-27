package de.hinundhergestellt.jhuh.sync

import com.shopify.admin.types.ProductVariant
import com.vaadin.flow.spring.annotation.VaadinSessionScope
import de.hinundhergestellt.jhuh.service.ready2order.ArtooDataStore
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedCategory
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedProduct
import de.hinundhergestellt.jhuh.service.ready2order.SingleArtooMappedProduct
import de.hinundhergestellt.jhuh.service.shopify.ShopifyDataStore
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.Assert
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.CompletableFuture.failedFuture
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.jvm.optionals.asSequence

private val logger = KotlinLogging.logger {}

@Service
@VaadinSessionScope
class ArtooImportService(
    private val artooDataStore: ArtooDataStore,
    private val shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
    private val syncVariantRepository: SyncVariantRepository
) {
    val rootCategories by artooDataStore::rootCategories

    fun getItemName(item: Any) =
        when (item) {
            is ArtooMappedCategory -> item.name
            is ArtooMappedProduct -> item.getName()
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun getItemVariations(item: Any) =
        when (item) {
            is ArtooMappedCategory -> null
            is SingleArtooMappedProduct -> 0
            is ArtooMappedProduct -> item.variations.size
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun isMarkedForSync(product: ArtooMappedProduct) =
        product.variations.asSequence()
            .map { it.barcode.asSequence() }
            .flatten()
            .any { syncVariantRepository.existsByBarcode(it) }

    fun filterByReadyToSync(item: Any) =
        when (item) {
            is ArtooMappedCategory -> item.containsReadyForSync()
            is ArtooMappedProduct -> item.isReadyForSync
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun filterByMarkedWithErrors(item: Any): Boolean =
        when (item) {
            is ArtooMappedCategory -> (item.children.asSequence() + item.products.asSequence()).any { filterByMarkedWithErrors(it) }
            is ArtooMappedProduct -> isMarkedForSync(item) && !item.isReadyForSync
            else -> throw IllegalStateException("Unexpected item $item")
        }

    fun markForSync(item: Any) {
    }

    fun unmarkForSync(item: Any) {
    }

    @Async
    @Transactional
    fun syncWithShopify(): CompletableFuture<Void?> {
        try {
            shopifyDataStore.products.forEach { reconcileFromShopify(it) }

            //            checkAllArtooVariationsSynced(shopifyProducts);
            return completedFuture<Void?>(null)
        } catch (e: RuntimeException) {
            logger.error(e) { "Couldn't synchronize with Shopify" }
            return failedFuture<Void?>(e)
        }
    }

    private fun reconcileFromShopify(product: ShopifyProduct) {
        val syncProduct = syncProductRepository
            .findByShopifyId(product.id)
            ?: syncProductRepository.save(SyncProduct(product.id, product.tags))
        product.variants.forEach { reconcileFromShopify(it, syncProduct) }
    }

    private fun reconcileFromShopify(variant: ProductVariant, syncProduct: SyncProduct) {
        val syncVariant = syncVariantRepository
            .findByBarcode(variant.barcode)
            ?: SyncVariant(syncProduct, variant.barcode)

        require(syncVariant.product === syncProduct) { "SyncVariant.product does not match ShopifyProduct" }
    }

    private fun reconcileFromArtoo() {
    }

    private fun checkAllArtooVariationsSynced(shopifyProducts: MutableList<ShopifyProduct?>) {
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
