package de.hinundhergestellt.jhuh

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.MariaDBContainer

@Configuration
class TestConfiguration {

    @Bean
    @ServiceConnection
    fun mariaDBContainer(): MariaDBContainer<*> =
        MariaDBContainer("mariadb")
            .withDatabaseName("hinundhergestellt")
            .withReuse(true)
}
