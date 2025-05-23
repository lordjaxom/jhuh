package de.hinundhergestellt.jhuh;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.HttpResponse;
import de.hinundhergestellt.jhuh.ready2order.ApiClient;
import de.hinundhergestellt.jhuh.ready2order.RateLimitEnforcingApiClient;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsIdPutRequestMixin;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsIdPutRequest;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsPostRequest;
import de.hinundhergestellt.jhuh.ready2order.model.ProductsPostRequestMixin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class HuhApplication {

    private final RestTemplateBuilder restTemplateBuilder;

    public static void main(String[] args) {
        SpringApplication.run(HuhApplication.class, args);
    }

    public HuhApplication(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplateBuilder = restTemplateBuilder;
    }

    @Bean
    public ObjectMapper objectMapper() {
        var objectMapper = new ObjectMapper();
        objectMapper.addMixIn(ProductsIdPutRequest.class, ProductsIdPutRequestMixin.class);
        objectMapper.addMixIn(ProductsPostRequest.class, ProductsPostRequestMixin.class);
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        return objectMapper;
    }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        return new MappingJackson2HttpMessageConverter(objectMapper());
    }

    @Bean
    public RestTemplate restTemplate() {
        return restTemplateBuilder
                .messageConverters(mappingJackson2HttpMessageConverter())
                .build();
    }

    @Bean
    public ApiClient ready2orderApiClient() {
        var apiClient = new RateLimitEnforcingApiClient(restTemplate());
        apiClient.setBasePath("https://api.ready2order.com/v1");
        apiClient.setApiKey("${READY2ORDER_APIKEY}");
        return apiClient;
    }

    @Bean
    public GraphQLClient shopifyApiClient() {
        var baseUrl = "https://${SHOPIFY_DOMAIN}.myshopify.com/admin/api/2025-04/graphql.json";
        var token = "${SHOPIFY_TOKEN}";
        var restTemplate = new RestTemplate();
        return GraphQLClient.createCustom(baseUrl, (url, headers, body) -> {
            var httpHeaders = new HttpHeaders();
            //httpHeaders.putAll(headers);
            headers.forEach(httpHeaders::addAll);
            httpHeaders.add("X-Shopify-Access-Token", token);

            System.out.println("Query: " + body);
            var response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
            return new HttpResponse(response.getStatusCode().value(), response.getBody());
        });
    }
}
