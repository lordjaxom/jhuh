package de.hinundhergestellt.jhuh.service.ready2order;

import de.hinundhergestellt.jhuh.core.task.Futures;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductClient;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroupClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.stream.Stream.concat;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtooDataStore {

    private final Lazy<List<ArtooMappedCategory>> rootCategories;

    public ArtooDataStore(ArtooProductGroupClient productGroupClient,
                          ArtooProductClient productClient,
                          @Qualifier("applicationTaskExecutor") AsyncTaskExecutor taskExecutor) {
        var groups = taskExecutor.submit(() -> productGroupClient.findAll().toList());
        var products = taskExecutor.submit(() -> productClient.findAll().toList());
        rootCategories = Lazy.of(() -> new DataStoreBuilder(Futures.get(groups), Futures.get(products)).rootCategories());
    }

    public List<ArtooMappedCategory> getRootCategories() {
        return rootCategories.get();
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static class DataStoreBuilder {

        private final List<ArtooProductGroup> groups;
        private final List<ArtooProduct> products;

        DataStoreBuilder(List<ArtooProductGroup> groups, List<ArtooProduct> products) {
            this.groups = groups;
            this.products = products;
        }

        List<ArtooMappedCategory> rootCategories() {
            return groups.stream()
                    .filter(it -> it.getParent() == 0)
                    .map(this::category)
                    .toList();
        }

        private ArtooMappedCategory category(ArtooProductGroup group) {
            var children = groups.stream()
                    .filter(it -> it.getTypeId() == 7)
                    .filter(it -> it.getParent() == group.getId())
                    .map(this::category)
                    .toList();

            var productsWithVariations = groups.stream()
                    .filter(it -> it.getTypeId() == 3)
                    .filter(it -> it.getParent() == group.getId())
                    .map(this::product);
            var singleProducts = products.stream()
                    .filter(it -> it.getProductGroupId() == group.getId())
                    .map(this::product);
            var products = concat(productsWithVariations, singleProducts).toList();

            return new ArtooMappedCategory(group, children, products);
        }

        private ArtooMappedProduct product(ArtooProductGroup group) {
            var variations = products.stream()
                    .filter(it -> it.getProductGroupId() == group.getId())
                    .map(ArtooMappedVariation::new)
                    .toList();

            return new GroupArtooMappedProduct(group, variations);
        }

        private ArtooMappedProduct product(ArtooProduct product) {
            return new SingleArtooMappedProduct(product);
        }
    }
}
