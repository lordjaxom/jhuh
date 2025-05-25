package de.hinundhergestellt.jhuh.vendors.shopify;

import com.shopify.admin.types.Product;
import com.shopify.admin.types.ProductVariant;
import com.shopify.admin.types.ProductVariantEdge;
import org.springframework.util.Assert;

import java.util.List;
import java.util.stream.Stream;

public class ShopifyProduct {

    private final Product product;

    ShopifyProduct(Product product) {
        this.product = product;
    }

    public String getHandle() {
        return product.getHandle();
    }

    public String getId() {
        return product.getId();
    }

    public String getTitle() {
        return product.getTitle();
    }

    public void setTitle(String title) {
        product.setTitle(title);
    }

    public String getProductType() {
        return product.getProductType();
    }

    public void setProductType(String productType) {
        product.setProductType(productType);
    }

    public List<String> getTags() {
        return product.getTags();
    }

    public void setTags(List<String> tags) {
        product.setTags(tags);
    }

    public String getVendor() {
        return product.getVendor();
    }

    public void setVendor(String vendor) {
        product.setVendor(vendor);
    }

    public boolean getHasOnlyDefaultVariant() {
        return product.getHasOnlyDefaultVariant();
    }

    public Stream<ProductVariant> getVariants() {
        Assert.isTrue(!product.getVariants().getPageInfo().getHasNextPage(), "Product has more variants than were loaded");
        return product.getVariants().getEdges().stream().map(ProductVariantEdge::getNode);
    }
}
