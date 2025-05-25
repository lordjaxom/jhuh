package de.hinundhergestellt.jhuh.sync;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProduct;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductClient;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup;
import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroupClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@VaadinSessionScope
public class ArtooImportService {

    private final List<ArtooProductGroup> productGroups;
    private final List<ArtooProduct> products;

    public ArtooImportService(ArtooProductGroupClient productGroupClient,
                              ArtooProductClient productClient){
        productGroups = productGroupClient.findAll();
        products = productClient.findAll();
    }

    public Stream<ArtooProductGroup> findRootProductGroups() {
        return productGroups.stream().filter(it -> it.getParent() == 0 && it.getTypeId() != 0);
    }

    public Stream<ArtooProductGroup> findProductGroupsByParent(ArtooProductGroup parent) {
        return productGroups.stream().filter(it -> it.getParent() == parent.getId());
    }

    public Stream<ArtooProduct> findProductsByProductGroup(ArtooProductGroup productGroup){
        return products.stream().filter(it -> it.getProductGroupId() == productGroup.getId());
    }

    public boolean isSyncable(ArtooProductGroup productGroup) {
        return findProductsByProductGroup(productGroup).allMatch(it -> it.getBarcode().isPresent());
    }

    public boolean isSyncable(ArtooProduct product) {
        return product.getBarcode().isPresent();
    }

    public boolean isMarkedForSync(ArtooProductGroup group) {
        return false;
    }

    public void markForSync(ArtooProductGroup group) {

    }

    public void unmarkForSync(ArtooProductGroup group) {

    }

    public boolean isMarkedForSync(ArtooProduct product){
        return false;
    }

    public void markForSync(ArtooProduct product) {

    }

    public void unmarkForSync(ArtooProduct product) {

    }
}
