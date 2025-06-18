package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.HasValue
import com.vaadin.flow.component.ItemLabelGenerator
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.binder.Binder

fun HasComponents.button(label: String? = null, block: Button.() -> Unit = {}) = init(Button(label), block)
fun HasComponents.button(icon: VaadinIcon, block: Button.() -> Unit = {}) = init(Button(icon.create()), block)
fun HasComponents.checkbox(label: String? = null, block: Checkbox.() -> Unit = {}) = init(Checkbox(label), block)
fun <T> HasComponents.comboBox(label: String? = null, block: ComboBox<T>.() -> Unit = {}) = init(ComboBox(label), block)
fun HasComponents.textArea(label: String? = null, block: TextArea.() -> Unit = {}) = init(TextArea(label), block)
fun HasComponents.textField(label: String? = null, block: TextField.() -> Unit = {}) = init(TextField(label), block)
fun HasComponents.verticalLayout(block: VerticalLayout.() -> Unit = {}) = init(VerticalLayout(), block)

fun <T> ComboBox<T>.itemLabelGenerator(valueProvider: (T) -> String) {
    itemLabelGenerator = ItemLabelGenerator { valueProvider(it) }
}

fun Dialog.header(block: Dialog.DialogHeader.() -> Unit) = header.block()
fun Dialog.footer(block: Dialog.DialogFooter.() -> Unit) = footer.block()

fun <BEAN, FIELDVALUE> HasValue<*, FIELDVALUE>.bind(binder: Binder<BEAN>) = binder.forField(this)

private fun <T: Component> HasComponents.init(component: T, block: T.() -> Unit = {}) =
    component.also { add(it); it.block() }