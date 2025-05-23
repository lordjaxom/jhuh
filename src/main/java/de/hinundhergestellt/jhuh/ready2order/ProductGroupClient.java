package de.hinundhergestellt.jhuh.ready2order;

import de.hinundhergestellt.jhuh.ready2order.api.ProductGroupApi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

@Component
public class ProductGroupClient {

    private final ProductGroupApi api;

    public ProductGroupClient(ApiClient apiClient) {
        api = new ProductGroupApi(apiClient);
    }

    public List<ProductGroup> findAll() {
        return api.productgroupsGet(null, null).stream()
                .map(ProductGroup::new)
                .toList();
    }

    public Map<String, ProductGroup> findAllMappedByPath() {
        var productGroups = findAll();
        return productGroups.stream()
                .collect(Collectors.toMap(
                        it -> it.getPath(productGroups),
                        identity()
                ));
    }

    public void save(ProductGroup productGroup) {
        productGroup.save(api);
    }
}
