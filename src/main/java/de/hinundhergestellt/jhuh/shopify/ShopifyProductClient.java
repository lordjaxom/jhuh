package de.hinundhergestellt.jhuh.shopify;

import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest;
import com.shopify.admin.client.ProductCreateGraphQLQuery;
import com.shopify.admin.client.ProductCreateProjectionRoot;
import com.shopify.admin.client.ProductsGraphQLQuery;
import com.shopify.admin.client.ProductsProjectionRoot;
import com.shopify.admin.types.PageInfo;
import com.shopify.admin.types.Product;
import com.shopify.admin.types.ProductConnection;
import com.shopify.admin.types.ProductCreateInput;
import com.shopify.admin.types.ProductCreatePayload;
import com.shopify.admin.types.ProductEdge;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Component
public class ShopifyProductClient {

    private final GraphQLClient apiClient;

    public ShopifyProductClient(GraphQLClient shopifyApiClient) {
        this.apiClient = shopifyApiClient;
    }

    public Stream<Product> findAll() {
        return PagingIterator.stream(this::findNextPage);
    }

    public void save(Product product) {
        if (product.getId() != null) {
            throw new IllegalArgumentException("Product id must be null");
        }

        var productInput = new ProductCreateInput();
        productInput.setTitle(product.getTitle());
        productInput.setVendor(product.getVendor());
        productInput.setProductType(product.getProductType());
        productInput.setTags(product.getTags());

        var query = ProductCreateGraphQLQuery.newRequest()
                .product(productInput)
                .build();

        // @formatter:off
        var root = new ProductCreateProjectionRoot<>()
                .product()
                    .id()
                    .parent()
                .userErrors()
                    .message()
                    .field();
        // @formatter:on

        var request = new GraphQLQueryRequest(query, root);
        var response = apiClient.executeQuery(request.serialize());
        var payload = response.extractValueAsObject("productCreate", ProductCreatePayload.class);
        if (!payload.getUserErrors().isEmpty()) {
            throw new IllegalArgumentException("Product creation failed: " + payload.getUserErrors());
        }
        product.setId(payload.getProduct().getId());
    }

    private Pair<Stream<Product>, PageInfo> findNextPage(String after) {
        var query = ProductsGraphQLQuery.newRequest()
                .first(100)
                .after(after)
                .build();

        var root = new ProductsProjectionRoot<>();
        var edgeProjection = root.edges();
        var productProjection = edgeProjection.node();
        productProjection.handle();
        productProjection.id();
        productProjection.title();
        productProjection.status();
        productProjection.tags();
        productProjection.vendor();

        var variantConnectionProjection = productProjection.variants(100, null, null, null, null, null);
        var variantEdgeProjection = variantConnectionProjection.edges();
        var variantProjection = variantEdgeProjection.node();
        variantProjection.barcode();
        variantProjection.compareAtPrice();
        variantProjection.id();
        variantProjection.price();
        variantProjection.sku();
        var inventoryItemProjection = variantProjection.inventoryItem();
        inventoryItemProjection.id();

        var pageInfoProjection = root.pageInfo();
        pageInfoProjection.endCursor();
        pageInfoProjection.hasNextPage();
        pageInfoProjection.startCursor();

        var request = new GraphQLQueryRequest(query, root);
        var response = apiClient.executeQuery(request.serialize());
        var payload = response.extractValueAsObject("products", ProductConnection.class);
        return Pair.ofNonNull(payload.getEdges().stream().map(ProductEdge::getNode), payload.getPageInfo());
    }
}
