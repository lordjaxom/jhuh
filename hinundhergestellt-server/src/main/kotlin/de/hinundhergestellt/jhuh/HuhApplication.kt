package de.hinundhergestellt.jhuh

import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class HuhApplication

fun main(args: Array<String>) {
    runApplication<HuhApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}