package de.hinundhergestellt.jhuh.ready2order;

import de.hinundhergestellt.jhuh.ready2order.api.ProductApi;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductClient {

    private final ProductApi api;

    public ProductClient(ApiClient apiClient) {
        api = new ProductApi(apiClient);
    }

    public List<Product> findAll() {
        return api.productsGet(null, null, null, null, null, null, null, true, null, null).stream()
                .map(Product::new)
                .toList();
    }

    public Product findById(int id) {
        return new Product(api.productsIdGet(id, true, null, null));
    }

    public void save(Product product) {
        product.save(api);
    }
}
