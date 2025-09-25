package de.hinundhergestellt.jhuh.backend.mapping

import java.math.BigDecimal
import kotlin.reflect.KMutableProperty0

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
    PRODUCT_TECHNICAL_DETAILS("Technische Daten", false, "technical_details"),
    PRODUCT_VENDOR_ADDRESS("Herstelleradresse", false, "vendor_address"),
    PRODUCT_VENDOR_EMAIL("Hersteller-Email", false, "vendor_email"),
    PRODUCT_OPTION_NAME("Optionsname", true, "optionName"),
    VARIANT_BARCODE("Barcode", true, "barcode"),
    VARIANT_SKU("Artikelnummer", true, "sku"),
    VARIANT_PRICE("Preis", true, "price"),
    VARIANT_WEIGHT("Gewicht", true, "weight"),
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

internal fun <T> change(getter: () -> T, setter: (T) -> Unit, newValue: T, field: ChangeField, vararg args: Any) =
    getter().takeIf { it != newValue }
        ?.let { changeMessage(field, it, newValue, *args) }
        ?.let { Change<Any?>(it) { setter(newValue) } }

internal fun <T> changeMessage(oldValue: T, newValue: T, field: ChangeField, vararg args: Any) =
    if (!oldValue.isEqualTo(newValue)) changeMessage(field, oldValue, newValue, *args) else null

internal fun <T> changeMessage(oldValue: T, newValue: T, fieldName: String, vararg args: Any) =
    changeMessage(oldValue, newValue, ChangeField.fromFieldName(fieldName), *args)

private fun <T> changeMessage(field: ChangeField, oldValue: T, newValue: T, vararg args: Any) =
    field.displayName.format(*args) +
            (if (field.showChange && !oldValue.isNullOrEmpty()) " von ${oldValue.toQuotedString()}" else "") +
            (if (field.showChange) " zu ${newValue.toQuotedString()}" else "") +
            " geändert"

internal fun Any?.toQuotedString() = when {
    this is Collection<*> -> joinToString(", ", prefix = "\"", postfix = "\"")
    else -> "\"${this ?: ""}\""
}

private fun Any?.isNullOrEmpty() = when {
    this == null -> true
    this is String -> isEmpty()
    this is Collection<*> -> isEmpty()
    else -> false
}

private fun <T> T.isEqualTo(other: T) = when {
    other == null -> this == null
    this is BigDecimal -> compareTo(other as BigDecimal) == 0
    else -> this == other
}