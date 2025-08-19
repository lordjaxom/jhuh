package de.hinundhergestellt.jhuh.tools

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class ToolsConfiguration {

    @Bean
    fun toolsWebClient() =
        WebClient.builder()
            .codecs { it.defaultCodecs().maxInMemorySize(5 * 1024 * 1024) }
            .build()
}