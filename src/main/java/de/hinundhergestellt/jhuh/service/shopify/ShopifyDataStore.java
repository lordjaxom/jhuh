package de.hinundhergestellt.jhuh.service.shopify;

import de.hinundhergestellt.jhuh.core.task.Futures;
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProduct;
import de.hinundhergestellt.jhuh.vendors.shopify.ShopifyProductClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.util.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ShopifyDataStore {

    private final Lazy<List<ShopifyProduct>> products;

    public ShopifyDataStore(ShopifyProductClient productClient,
                            AsyncTaskExecutor taskExecutor) {
        var loadProducts = taskExecutor.submit(() -> productClient.findAll().toList());
        this.products = Lazy.of(() -> Futures.get(loadProducts));
    }

    public List<ShopifyProduct> getProducts() {
        return products.get();
    }
}
