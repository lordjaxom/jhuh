package de.hinundhergestellt.jhuh

import io.netty.channel.ChannelOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Scope
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
@ConfigurationPropertiesScan
class BaseConfiguration {

    @Bean // TODO: Cancellation
    fun applicationScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    fun webClientBuilder(): WebClient.Builder {
        val client = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .responseTimeout(Duration.ofSeconds(120))

        return WebClient.builder()
            .codecs { it.defaultCodecs().maxInMemorySize(5 * 1024 * 1024) }
            .clientConnector(ReactorClientHttpConnector(client))
    }

    @Bean
    @Primary
    fun genericWebClient(builder: WebClient.Builder) = builder.build()
}