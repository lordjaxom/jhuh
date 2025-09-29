@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.backend.mapping

import java.math.BigDecimal
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal enum class ChangeField(
    val displayName: String,
    val showChange: Boolean,
    vararg val fieldName: String
) {
    PRODUCT_TITLE("Titel", true, "title"),
    PRODUCT_VENDOR("Hersteller", true, "vendor"),
    PRODUCT_TYPE("Produktart", true, "productType", "type"),
    PRODUCT_DESCRIPTION("Produktbeschreibung", false, "descriptionHtml"),
    PRODUCT_SEO_TITLE("SEO-Titel", true, "seoTitle"),
    PRODUCT_META_DESCRIPTION("Metabeschreibung", true, "seoDescription"),
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

internal fun <T, R> ifChanged(oldValue: T, newValue: T, field: ChangeField, vararg args: Any, block: (String) -> R): R? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (!oldValue.isEqualTo(newValue)) block(changeMessage(field, oldValue, newValue, *args)) else null
}

internal fun <T, R> ifChanged(oldValue: T, newValue: T, fieldName: String, vararg args: Any, block: (String) -> R) =
    ifChanged(oldValue, newValue, ChangeField.fromFieldName(fieldName), *args) { block(it) }

private fun <T> changeMessage(field: ChangeField, oldValue: T, newValue: T, vararg args: Any) = buildString {
    append(field.displayName.format(*args))
    if (field.showChange) {
        if (oldValue.isNullOrEmpty()) append(" ${newValue.toQuotedString()}")
        else append(" von ${oldValue.toQuotedString()} zu ${newValue.toQuotedString()}")
    }
    if (oldValue.isNullOrEmpty()) append(" hinzugefügt")
    else append(" geändert")
}

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
    this == null -> other.isNullOrEmpty()
    other == null -> isNullOrEmpty()
    this is BigDecimal -> compareTo(other as BigDecimal) == 0
    else -> this == other
}