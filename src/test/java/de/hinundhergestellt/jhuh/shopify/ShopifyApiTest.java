package de.hinundhergestellt.jhuh.shopify;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import com.shopify.admin.client.InventoryItemProjection;
import com.shopify.admin.client.PageInfoProjection;
import com.shopify.admin.client.ProductEdgeProjection;
import com.shopify.admin.client.ProductProjection;
import com.shopify.admin.client.ProductVariantConnectionProjection;
import com.shopify.admin.client.ProductVariantEdgeProjection;
import com.shopify.admin.client.ProductVariantProjection;
import com.shopify.admin.client.ProductsGraphQLQuery;
import com.shopify.admin.client.ProductsProjectionRoot;
import de.hinundhergestellt.jhuh.HuhApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

class ShopifyApiTest {

    private final HuhApplication application = new HuhApplication(new RestTemplateBuilder());

    @Test
    void getProducts() {
        var query = ProductsGraphQLQuery.newRequest()
                .first(250)
                .build();

        var root = new ProductsProjectionRoot<>();
        var edgeProjection = root.edges();
        var productProjection = edgeProjection.node();

        productProjection.handle();
        productProjection.id();
        productProjection.status();
        productProjection.tags();

        var variantConnectionProjection = productProjection.variants(10, null, null,
                null, null, null);
        var vEdgeProjection = variantConnectionProjection.edges();
        var variantProjection = vEdgeProjection.node();
        variantProjection.barcode();
        variantProjection.compareAtPrice();
        variantProjection.id();
        var inventoryItemProjection = variantProjection.inventoryItem();
        inventoryItemProjection.id();
        variantProjection.price();
        variantProjection.sku();

        productProjection.vendor();

        var pageInfoProjection = root.pageInfo();
        pageInfoProjection.endCursor();
        pageInfoProjection.hasNextPage();
        pageInfoProjection.startCursor();

        GraphQLQueryRequest request = new GraphQLQueryRequest(query, root);
        System.out.println(request.serialize());

        var client = application.shopifyApiClient();
        var response = client.executeQuery(request.serialize());
        System.out.println(response.toString());
    }
}

// Query: {"query":"{\n  products(first: 250) {\n    edges {\n      node {\n        handle\n        id\n        status\n        tags\n        variants(first: 10, after: null, last: null, before: null, reverse: null, sortKey: null) {\n          edges {\n            node {\n              barcode\n              compareAtPrice\n              id\n              inventoryItem {\n                id\n              }\n              price\n              sku\n            }\n          }\n        }\n        vendor\n      }\n    }\n    pageInfo {\n      endCursor\n      hasNextPage\n      startCursor\n    }\n  }\n}","operationName":null,"variables":{}}
// Query: {"query":"{\n  products(first: 250) {\n    edges {\n      node {\n        handle\n        id\n        status\n        tags\n        variants(first: 10, after: null, last: null, before: null, reverse: null, sortKey: null) {\n          edges {\n            node {\n              barcode\n              compareAtPrice\n              id\n              inventoryItem {\n                id\n              }\n              price\n              sku\n            }\n          }\n        }\n        vendor\n      }\n    }\n    pageInfo {\n      endCursor\n      hasNextPage\n      startCursor\n    }\n  }\n}","operationName":null,"variables":{}}