package de.hinundhergestellt.jhuh.ready2order;

import de.hinundhergestellt.jhuh.ready2order.api.ProductGroupApi;
import de.hinundhergestellt.jhuh.ready2order.model.ProductgroupsGet200ResponseInner;
import de.hinundhergestellt.jhuh.ready2order.model.ProductgroupsPostRequest;
import org.springframework.lang.Nullable;

import static java.util.Objects.requireNonNull;

public class ProductGroup {

    private ProductgroupsGet200ResponseInner value;

    public ProductGroup(String name, String description, String shortcut, boolean active, @Nullable Integer parent, int sortIndex,
                        int typeId) {
        value = new ProductgroupsGet200ResponseInner();
        value.setProductgroupName(name);
        value.setProductgroupDescription(description);
        value.setProductgroupShortcut(shortcut);
        value.setProductgroupActive(active);
        value.setProductgroupParent(parent);
        value.setProductgroupSortIndex(sortIndex);
        value.setProductgroupTypeId(typeId);
    }

    ProductGroup(ProductgroupsGet200ResponseInner value) {
        this.value = value;
    }

    public int getId() {
        return requireNonNull(value.getProductgroupId());
    }

    public String getName() {
        return requireNonNull(value.getProductgroupName());
    }

    void save(ProductGroupApi api) {
        var request = toPostRequest();
        if (value.getProductgroupId() != null) {
            value = api.productgroupsIdPut(value.getProductgroupId(), request);
        } else {
            value = api.productgroupsPost(request);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }

    private ProductgroupsPostRequest toPostRequest() {
        var request = new ProductgroupsPostRequest();
        request.setProductgroupName(value.getProductgroupName());
        request.setProductgroupDescription(value.getProductgroupDescription());
        request.setProductgroupShortcut(value.getProductgroupShortcut());
        request.setProductgroupActive(value.getProductgroupActive());
        request.setProductgroupParent(value.getProductgroupParent());
        request.setProductgroupSortIndex(value.getProductgroupSortIndex());
        request.setProductgroupTypeId(value.getProductgroupTypeId());
        return request;
    }
}
