package de.hinundhergestellt.jhuh;

import de.hinundhergestellt.jhuh.ready2order.ApiClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HuhApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuhApplication.class, args);
    }

    @Bean
    public ApiClient apiClient() {
        var apiClient = new ApiClient();
        apiClient.setApiKey("${READY2ORDER_APIKEY}");
        return apiClient;
    }
}
