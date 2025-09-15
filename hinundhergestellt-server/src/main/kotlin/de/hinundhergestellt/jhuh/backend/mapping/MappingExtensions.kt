package de.hinundhergestellt.jhuh.backend.mapping

import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KMutableProperty1

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
    "price" to Pair("Preis", true),
    "technical_details" to Pair("Technische Daten", false)
)

internal fun <T> changeMessage(fieldName: String, oldValue: T, newValue: T): String {
    val field = FIELD_NAME_TO_READABLE[fieldName] ?: Pair("Property $fieldName", false)
    return "${field.first}${if (field.second) " von ${oldValue.toQuotedString()} zu ${newValue.toQuotedString()}" else ""} geändert"
}

internal fun <T> additionMessage(fieldName: String, value: T): String {
    val field = FIELD_NAME_TO_READABLE[fieldName] ?: Pair("Property $fieldName", false)
    return "${field.first}${if (field.second) " ${value.toQuotedString()}" else ""} hinzugefügt"
}

internal enum class ChangeField(
    val displayName: String,
    val showChange: Boolean,
    vararg val fieldName: String
) {
    PRODUCT_TITLE("Titel", true, "title"),
    PRODUCT_VENDOR("Hersteller", true, "vendor"),
    PRODUCT_TYPE("Produktart", true, "productType", "type"),
    PRODUCT_DESCRIPTION("Produktbeschreibung", false, "descriptionHtml"),
    PRODUCT_TAGS("Tags", true, "tags"),
    VARIANT_BARCODE("Barcode", true, "barcode"),
    VARIANT_SKU("Artikelnummer", true, "sku"),
    VARIANT_PRICE("Preis", true, "price"),
    OPTION_VALUE("Option %1\$s", true);

    companion object {
        fun fromFieldName(fieldName: String) = entries.first { it.fieldName.contains(fieldName) }
    }
}

internal class Change<T>(
    val message: String,
    val action: T.() -> Unit
)

internal fun <T> change(property: KMutableProperty0<T>, newValue: T, field: ChangeField? = null, vararg args: Any) =
    property.get().takeIf { it != newValue }
        ?.let { changeMessage(field ?: ChangeField.fromFieldName(property.name), it, newValue, *args) }
        ?.let { Change<Any?>(it) { property.set(newValue) } }

internal fun <T, V> change(instance: T, property: KMutableProperty1<T, V>, newValue: V, field: ChangeField? = null, vararg args: Any) =
    property.get(instance).takeIf { it != newValue }
        ?.let { changeMessage(field ?: ChangeField.fromFieldName(property.name), it, newValue, *args) }
        ?.let { Change<T>(it) { property.set(this, newValue) } }

private fun <T> changeMessage(field: ChangeField, oldValue: T, newValue: T, vararg args: Any) =
    "${field.displayName.format(*args)}${if (field.showChange) " von ${oldValue.toQuotedString()} zu ${newValue.toQuotedString()}" else ""} geändert"