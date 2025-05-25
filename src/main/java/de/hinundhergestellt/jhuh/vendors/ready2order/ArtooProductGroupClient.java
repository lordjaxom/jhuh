package de.hinundhergestellt.jhuh.vendors.ready2order;

import de.hinundhergestellt.jhuh.vendors.ready2order.api.ProductGroupApi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

@Component
public class ArtooProductGroupClient {

    private final ProductGroupApi api;

    public ArtooProductGroupClient(ApiClient apiClient) {
        api = new ProductGroupApi(apiClient);
    }

    public List<ArtooProductGroup> findAll() {
        return api.productgroupsGet(null, null).stream()
                .map(ArtooProductGroup::new)
                .toList();
    }

    public Map<String, ArtooProductGroup> findAllMappedByPath() {
        var productGroups = findAll();
        return productGroups.stream()
                .collect(Collectors.toMap(
                        it -> it.getPath(productGroups),
                        identity()
                ));
    }

    public void save(ArtooProductGroup productGroup) {
        productGroup.save(api);
    }
}
