package de.hinundhergestellt.jhuh.vendors.ready2order;

import de.hinundhergestellt.jhuh.vendors.ready2order.api.ProductGroupApi;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;

@Component
public class ArtooProductGroupClient {

    private final ProductGroupApi api;

    public ArtooProductGroupClient(ApiClient apiClient) {
        api = new ProductGroupApi(apiClient);
    }

    public Stream<ArtooProductGroup> findAll() {
        return PagingIterator.stream(this::findAll);
    }

    public Map<String, ArtooProductGroup> findAllMappedByPath() {
        var productGroups = findAll().toList();
        return productGroups.stream()
                .collect(Collectors.toMap(
                        it -> it.getPath(productGroups),
                        identity()
                ));
    }

    public void save(ArtooProductGroup productGroup) {
        productGroup.save(api);
    }

    private Stream<ArtooProductGroup> findAll(int page) {
        return api.productgroupsGet(page, null).stream().map(ArtooProductGroup::new);
    }
}
