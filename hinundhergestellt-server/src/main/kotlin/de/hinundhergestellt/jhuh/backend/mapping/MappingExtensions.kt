package de.hinundhergestellt.jhuh.backend.mapping

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KMutableProperty0

private val logger = KotlinLogging.logger {}

internal fun <T> KMutableProperty0<T>.update(value: T): Boolean {
    if (get() != value) {
        logger.info { "Property $name changed from ${get()} to $value" }
        set(value)
        return true
    }
    return false
}