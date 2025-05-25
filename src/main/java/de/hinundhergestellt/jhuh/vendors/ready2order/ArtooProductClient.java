package de.hinundhergestellt.jhuh.vendors.ready2order;

import de.hinundhergestellt.jhuh.vendors.ready2order.api.ProductApi;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Component
public class ArtooProductClient {

    private final ProductApi api;

    public ArtooProductClient(ApiClient apiClient) {
        api = new ProductApi(apiClient);
    }

    public Stream<ArtooProduct> findAll() {
        return PagingIterator.stream(this::findAll);
    }

    public ArtooProduct findById(int id) {
        return new ArtooProduct(api.productsIdGet(id, true, null, null));
    }

    public void save(ArtooProduct product) {
        product.save(api);
    }

    private Stream<ArtooProduct> findAll(int page) {
        return api.productsGet(page, null, null, null, null, null, null, true, null, null).stream()
                .map(ArtooProduct::new);
    }
}
