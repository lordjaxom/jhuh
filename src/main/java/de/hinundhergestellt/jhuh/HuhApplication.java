package de.hinundhergestellt.jhuh;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.graphql.dgs.client.GraphQLClient;
import com.netflix.graphql.dgs.client.HttpResponse;
import de.hinundhergestellt.jhuh.vendors.ready2order.ApiClient;
import de.hinundhergestellt.jhuh.vendors.ready2order.RateLimitEnforcingApiClient;
import de.hinundhergestellt.jhuh.vendors.ready2order.model.ProductsIdPutRequestMixin;
import de.hinundhergestellt.jhuh.vendors.ready2order.model.ProductsIdPutRequest;
import de.hinundhergestellt.jhuh.vendors.ready2order.model.ProductsPostRequest;
import de.hinundhergestellt.jhuh.vendors.ready2order.model.ProductsPostRequestMixin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@SpringBootApplication
@EnableAsync
public class HuhApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(HuhApplication.class)
                .bannerMode(Banner.Mode.OFF)
                .run(args);
    }

    @Bean
    public ApiClient ready2orderApiClient(@Value("${ready2order.apikey}") String apikey) {
        var objectMapper = new ObjectMapper();
        objectMapper.addMixIn(ProductsIdPutRequest.class, ProductsIdPutRequestMixin.class);
        objectMapper.addMixIn(ProductsPostRequest.class, ProductsPostRequestMixin.class);
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        var messageConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        var restTemplate = new RestTemplate(List.of(messageConverter));

        var apiClient = new RateLimitEnforcingApiClient(restTemplate);
        apiClient.setBasePath("https://api.ready2order.com/v1");
        apiClient.setApiKey(apikey);
        return apiClient;
    }

    @Bean
    public GraphQLClient shopifyApiClient(@Value("${shopify.domain}") String domain,
                                          @Value("${shopify.token}") String token) {
        var baseUrl = "https://" + domain + ".myshopify.com/admin/api/2025-04/graphql.json";
        var restTemplate = new RestTemplate();
        return GraphQLClient.createCustom(baseUrl, (url, headers, body) -> {
            var httpHeaders = new HttpHeaders();
            httpHeaders.putAll(headers);
            httpHeaders.add("X-Shopify-Access-Token", token);

            var response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, httpHeaders), String.class);
            return new HttpResponse(response.getStatusCode().value(), response.getBody());
        });
    }
}
