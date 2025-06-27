package de.hinundhergestellt.jhuh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class HuhApplication {
//
//    @Bean
//    fun googleSearchWebClient(
//        @Value("\${google.apikey}") apikey: String,
//        @Value("\${google.cx}") cx: String
//    ): WebClient {
//        return WebClient.builder()
//            .baseUrl("https://www.googleapis.com/customsearch/v1")
//            .defaultUriVariables(mapOf("key" to apikey, "cx" to cx))
//            .build()
//    }

    @Bean // TODO: Cancellation
    fun applicationScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

fun main(args: Array<String>) {
    runApplication<HuhApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}