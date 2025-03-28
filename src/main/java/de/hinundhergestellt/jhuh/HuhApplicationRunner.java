package de.hinundhergestellt.jhuh;

import de.hinundhergestellt.jhuh.ready2order.ApiClient;
import de.hinundhergestellt.jhuh.ready2order.api.ProductApi;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class HuhApplicationRunner implements ApplicationRunner {

    private final ApiClient apiClient;

    public HuhApplicationRunner(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var productApi = new ProductApi(apiClient);
        var response = productApi.productsGet(null, null, null, null, null, null, null, true, true, true);
        System.out.println(response);
    }
}
