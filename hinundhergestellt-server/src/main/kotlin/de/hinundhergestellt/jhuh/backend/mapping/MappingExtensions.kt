package de.hinundhergestellt.jhuh.backend.mapping

import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KMutableProperty1

internal fun Collection<*>.toQuotedString() = joinToString(", ", prefix = "\"", postfix = "\"")
internal fun Any?.toQuotedString() = if (this is Collection<*>) toQuotedString() else "\"${this ?: ""}\""

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
    PRODUCT_TECHNICAL_DETAILS("Technische Daten", false),
    VARIANT_BARCODE("Barcode", true, "barcode"),
    VARIANT_SKU("Artikelnummer", true, "sku"),
    VARIANT_PRICE("Preis", true, "price"),
    VARIANT_WEIGHT("Gewicht", true),
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

internal fun <T> change(getter: () -> T, setter: (T) -> Unit, newValue: T, field: ChangeField, vararg args: Any) =
    getter().takeIf { it != newValue }
        ?.let { changeMessage(field, it, newValue, *args) }
        ?.let { Change<Any?>(it) { setter(newValue) } }

internal fun <T> addition(setter: (T) -> Unit, newValue: T, field: ChangeField, vararg args: Any) =
    Change<Any?>(additionMessage(field, newValue, *args)) { setter(newValue) }

private fun <T> changeMessage(field: ChangeField, oldValue: T, newValue: T, vararg args: Any) =
    "${field.displayName.format(*args)}${if (field.showChange) " von ${oldValue.toQuotedString()} zu ${newValue.toQuotedString()}" else ""} geändert"

private fun <T> additionMessage(field: ChangeField, value: T, vararg args: Any) =
    "${field.displayName.format(*args)}${if (field.showChange) " ${value.toQuotedString()}" else ""} hinzugefügt"