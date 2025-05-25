package de.hinundhergestellt.jhuh.vendors.shopify;

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

    public Stream<ShopifyProduct> findAll() {
        return PagingIterator.stream(this::findAll);
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

    private Pair<Stream<ShopifyProduct>, PageInfo> findAll(String after) {
        var query = ProductsGraphQLQuery.newRequest()
                .first(100)
                .after(after)
                .build();

        // @formatter:off
        var root = new ProductsProjectionRoot<>()
                .edges()
                    .node()
                        .handle()
                        .id()
                        .title()
                        .vendor()
                        .productType()
                        .tags()
                        .variants(100, null, null, null, null, null)
                            .edges()
                                .node()
                                    .id()
                                    .title()
                                    .price()
                                    .sku()
                                    .barcode()
                                    //.inventoryItem().id().parent()
                                    .parent()
                                .parent()
                            .pageInfo()
                                .hasNextPage()
                                .parent()
                            .parent()
                        .parent()
                    .parent()
                .pageInfo()
                    .hasNextPage()
                    .endCursor();
        // @formatter:on

        var request = new GraphQLQueryRequest(query, root);
        var response = apiClient.executeQuery(request.serialize());
        if (response.hasErrors()) {
            throw new RuntimeException(response.getErrors().toString()); // TODO
        }
        var payload = response.extractValueAsObject("products", ProductConnection.class);
        return Pair.ofNonNull(
                payload.getEdges().stream()
                        .map(ProductEdge::getNode)
                        .map(ShopifyProduct::new),
                payload.getPageInfo()
        );
    }
}
