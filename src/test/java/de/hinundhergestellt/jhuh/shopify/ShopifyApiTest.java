package de.hinundhergestellt.jhuh.shopify;

import com.shopify.admin.types.Product;
import de.hinundhergestellt.jhuh.HuhApplication;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(classes = HuhApplication.class)
class ShopifyApiTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShopifyApiTest.class);

    @Autowired
    private ShopifyProductClient productClient;

    @Test
    void getsAllProducts() {
        productClient.findAll().forEach(it -> LOGGER.info("Product: {}", it.getTitle()));
    }

    @Test
    void createsNewProduct() {
        var product = new Product();
        product.setTitle("New Product");
        product.setDescription("New Description");
        product.setVendor("ACME");
        product.setProductType("Wolle");
        product.setTags(List.of("Wolle"));
        productClient.save(product);
        LOGGER.info("New Product: {}", product.getId());
    }
}

// Query: {"query":"{\n  products(first: 250) {\n    edges {\n      node {\n        handle\n        id\n        status\n        tags\n        variants(first: 10, after: null, last: null, before: null, reverse: null, sortKey: null) {\n          edges {\n            node {\n              barcode\n              compareAtPrice\n              id\n              inventoryItem {\n                id\n              }\n              price\n              sku\n            }\n          }\n        }\n        vendor\n      }\n    }\n    pageInfo {\n      endCursor\n      hasNextPage\n      startCursor\n    }\n  }\n}","operationName":null,"variables":{}}
// Query: {"query":"{\n  products(first: 250) {\n    edges {\n      node {\n        handle\n        id\n        status\n        tags\n        variants(first: 10, after: null, last: null, before: null, reverse: null, sortKey: null) {\n          edges {\n            node {\n              barcode\n              compareAtPrice\n              id\n              inventoryItem {\n                id\n              }\n              price\n              sku\n            }\n          }\n        }\n        vendor\n      }\n    }\n    pageInfo {\n      endCursor\n      hasNextPage\n      startCursor\n    }\n  }\n}","operationName":null,"variables":{}}