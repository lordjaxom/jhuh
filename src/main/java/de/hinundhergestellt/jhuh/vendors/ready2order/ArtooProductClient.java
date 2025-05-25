package de.hinundhergestellt.jhuh.vendors.ready2order;

import de.hinundhergestellt.jhuh.vendors.ready2order.api.ProductApi;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArtooProductClient {

    private final ProductApi api;

    public ArtooProductClient(ApiClient apiClient) {
        api = new ProductApi(apiClient);
    }

    public List<ArtooProduct> findAll() {
        return api.productsGet(null, null, null, null, null, null, null, true, null, null).stream()
                .map(ArtooProduct::new)
                .toList();
    }

    public ArtooProduct findById(int id) {
        return new ArtooProduct(api.productsIdGet(id, true, null, null));
    }

    public void save(ArtooProduct product) {
        product.save(api);
    }
}
