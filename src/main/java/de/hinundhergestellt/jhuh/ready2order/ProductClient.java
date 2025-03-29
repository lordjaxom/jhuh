package de.hinundhergestellt.jhuh.ready2order;

import de.hinundhergestellt.jhuh.ready2order.api.ProductApi;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsGet200ResponseInner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Component
public class ProductClient {

    private final ProductApi api;

    public ProductClient(ApiClient apiClient) {
        api = new ProductApi(apiClient);
    }

    public List<Product> findAll() {
        var productsAndVariations = api.productsGet(null, null, null, null, null, null, null, true, null, null);

        var rootProducts = productsAndVariations.stream()
                .filter(ProductClient::isRootProduct)
                .toList();
        var allVariations = productsAndVariations.stream()
                .filter(ProductClient::isVariation)
                .collect(Collectors.groupingBy(it -> requireNonNull(it.getProductBaseId())));
        return rootProducts.stream()
                .map(it -> createProduct(it, allVariations))
                .toList();
    }

    public void save(ProductVariation product) {
        product.save(api);
    }

    private static Product createProduct(ProductsGet200ResponseInner value, Map<Integer, List<ProductsGet200ResponseInner>> allVariations) {
        var variations = allVariations.getOrDefault(value.getProductId(), List.of()).stream()
                .map(ProductVariation::new)
                .toList();
        return new Product(value, variations);
    }

    private static boolean isRootProduct(ProductsGet200ResponseInner value) {
        return value.getProductType() == null;
    }

    private static boolean isVariation(ProductsGet200ResponseInner value) {
        return "variation".equals(value.getProductType());
    }
}
