package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.data.binder.ValidationException
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.components.bind
import de.hinundhergestellt.jhuh.components.binder
import de.hinundhergestellt.jhuh.components.to
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.checkbox
import de.hinundhergestellt.jhuh.components.comboBox
import de.hinundhergestellt.jhuh.components.footer
import de.hinundhergestellt.jhuh.components.header
import de.hinundhergestellt.jhuh.components.itemLabelGenerator
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.verticalLayout
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.CategoryItem
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.ProductItem
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class EditItemDialog(
    item: SyncableItem,
    vendors: List<SyncVendor>,
    private val callback: (EditItemResult?) -> Unit
) : Dialog() {

    private val binder = binder<EditItemResult>()
    private val result = EditItemResult(item)

    init {
        width = "500px"
        headerTitle = "${if (item is ProductItem) "Produkt" else "Kategorie"} bearbeiten"

        header {
            button(VaadinIcon.CLOSE) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY)
                addClickListener { close(); callback(null) }
            }
        }
        verticalLayout {
            isSpacing = false
            isPadding = false

            val vendorComboBox = comboBox<SyncVendor>("Hersteller") {
                isClearButtonVisible = true
                isEnabled = item is ProductItem
                itemLabelGenerator { it.name }
                setWidthFull()
                setItems(vendors)
                bind(binder).to(EditItemResult::vendor)
                if (item is ProductItem) focus()
                // TODO: Focus next on value change
            }
            if (item is CategoryItem) {
                checkbox("Für alle Produkte ersetzen?") {
                    addValueChangeListener { vendorComboBox.isEnabled = value; if (value) vendorComboBox.focus() }
                    bind(binder).to(EditItemResult::replaceVendor)
                }
            }
            val typeTextField = textField("Produktart") {
                isEnabled = item is ProductItem
                setWidthFull()
                bind(binder).to(EditItemResult::type)
            }
            if (item is CategoryItem) {
                checkbox("Für alle Produkte ersetzen?") {
                    addValueChangeListener { typeTextField.isEnabled = value; if (value) typeTextField.focus() }
                    bind(binder).to(EditItemResult::replaceType)
                }
            }
            textField("Tags") {
                setWidthFull()
                bind(binder).to(EditItemResult::tags)
                if (item is CategoryItem) focus()
            }
        }
        footer {
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickShortcut(Key.ENTER)
                addClickListener { save() }
            }
        }

        binder.readBean(result)
    }

    private fun save() {
        try {
            binder.writeBean(result)
            close()
            callback(result)
        } catch (_: ValidationException) {
        }
    }
}

class EditItemResult private constructor(
    var vendor: SyncVendor?,
    var replaceVendor: Boolean,
    var type: String,
    var replaceType: Boolean,
    var tags: String
) {
    constructor(item: SyncableItem) : this(
        vendor = item.vendor,
        replaceVendor = item is ProductItem,
        type = item.type ?: "",
        replaceType = item is ProductItem,
        tags = item.tags
    )
}

suspend fun editItemDialog(item: SyncableItem, vendors: List<SyncVendor>) =
    suspendCancellableCoroutine {
        val dialog = EditItemDialog(item, vendors) { result -> it.resume(result) }
        it.invokeOnCancellation { dialog.close() }
        dialog.open()
    }
