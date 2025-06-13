package de.hinundhergestellt.jhuh.usecases.products

import arrow.core.Option
import arrow.core.Some
import arrow.core.none
import com.vaadin.flow.component.Focusable
import com.vaadin.flow.component.ItemLabelGenerator
import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.CategoryItem
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.ProductItem

class EditItemDialog(
    private val item: SyncableItem,
    private val vendors: List<SyncVendor>,
    private val saveListener: (vendor: Option<SyncVendor>?, type: Option<String>?, tags: String) -> Unit
) : Dialog() {

    init {
        width = "500px"
        headerTitle = "${if (item is ProductItem) "Produkt" else "Kategorie"} bearbeiten"

        val closeButton = Button(VaadinIcon.CLOSE.create()).apply {
            addThemeVariants(ButtonVariant.LUMO_TERTIARY)
            addClickListener { close() }
        }
        header.add(closeButton)

        val bodyLayout = VerticalLayout().apply {
            isSpacing = false
            isPadding = false
        }
        add(bodyLayout)

        val vendorComboBox = ComboBox<SyncVendor>().apply {
            label = "Hersteller"
            itemLabelGenerator = ItemLabelGenerator { it.name }
            setItems(vendors)
            value = item.vendor
            isEnabled = item is ProductItem
            setWidthFull()
        }
        // TODO: Focus next on value change
        bodyLayout.add(vendorComboBox)

        val vendorCheckbox =
            if (item is CategoryItem) replaceForAllCheckbox(vendorComboBox).also { bodyLayout.add(it) }
            else null

        val typeTextField = textField("Produktart", item.type, item is ProductItem)
        bodyLayout.add(typeTextField)

        val typeCheckbox =
            if (item is CategoryItem) replaceForAllCheckbox(typeTextField).also { bodyLayout.add(it) }
            else null

        val tagsTextField = textField("Tags", item.tags)
        bodyLayout.add(tagsTextField)

        sequenceOf<Focusable<*>>(vendorComboBox, tagsTextField).first { it.isEnabled }.focus()

        val saveButton = Button("Speichern").apply {
            addThemeVariants(ButtonVariant.LUMO_PRIMARY)
            addClickShortcut(Key.ENTER)
            addClickListener {
                isEnabled = false
                val vendor =
                    if (vendorCheckbox != null && !vendorCheckbox.value) null
                    else Option.fromNullable(vendorComboBox.value)
                val type =
                    if (typeCheckbox != null && !typeCheckbox.value) null
                    else if (typeTextField.value.isNotEmpty()) Some(typeTextField.value)
                    else none()
                saveListener(vendor, type, tagsTextField.value)
                close()
            }
        }
        footer.add(saveButton)

        open()
    }

    private fun replaceForAllCheckbox(component: Focusable<*>) =
        Checkbox("Für alle Produkte ersetzen?").apply {
            addValueChangeListener {
                component.isEnabled = it.value
                if (it.value) component.focus()
            }
        }

    private fun textField(label: String, value: String?, enabled: Boolean = true) =
        TextField().also {
            it.label = label
            it.value = value ?: ""
            it.isEnabled = enabled
            it.setWidthFull()
        }
}