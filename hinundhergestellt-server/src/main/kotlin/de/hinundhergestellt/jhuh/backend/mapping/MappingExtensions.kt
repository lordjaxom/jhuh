package de.hinundhergestellt.jhuh.backend.mapping

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

private val logger = KotlinLogging.logger {}

internal fun <T> KMutableProperty0<T>.update(value: T): Boolean {
    if (get() != value) {
        logger.info { "Property $name changed from ${get()} to $value" }
        set(value)
        return true
    }
    return false
}

internal fun Collection<*>.toQuotedString() = joinToString(", ", prefix = "\"", postfix = "\"")
internal fun Any?.toQuotedString() = if (this is Collection<*>) toQuotedString() else "\"${this ?: ""}\""

private val FIELD_NAME_TO_READABLE = mapOf(
    "title" to Pair("Titel", true),
    "vendor" to Pair("Hersteller", true),
    "productType" to Pair("Produktart", true),
    "descriptionHtml" to Pair("Beschreibung", false),
    "tags" to Pair("Tags", true),
    "vendor_email" to Pair("Hersteller-Email", true),
    "vendor_address" to Pair("Herstelleradresse", false),
    "product_specs" to Pair("Produktspezifikationen", false),
    "barcode" to Pair("Barcode", true),
    "sku" to Pair("Artikelnummer", true),
    "price" to Pair("Preis", true)
)

internal fun <T> KProperty0<T>.changeMessage(newValue: T) = changeMessage(name, get(), newValue)

internal fun <T> changeMessage(fieldName: String, oldValue: T, newValue: T): String {
    val field = FIELD_NAME_TO_READABLE[fieldName] ?: Pair("Property $fieldName", false)
    return "${field.first}${if (field.second) " von ${oldValue.toQuotedString()} zu ${newValue.toQuotedString()}" else ""} geändert"
}

internal fun <T> additionMessage(fieldName: String, value: T): String {
    val field = FIELD_NAME_TO_READABLE[fieldName] ?: Pair("Property $fieldName", false)
    return "${field.first}${if (field.second) " ${value.toQuotedString()}" else ""}"
}