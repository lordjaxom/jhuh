package de.hinundhergestellt.jhuh.usecases.products

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KMutableProperty0

private val logger = KotlinLogging.logger {}

internal fun <T> updateProperty(property: KMutableProperty0<T>, value: T): Boolean {
    if (property.get() != value) {
        logger.info { "Property ${property.name} changed from ${property.get()} to $value" }
        property.set(value)
        return true
    }
    return false
}
