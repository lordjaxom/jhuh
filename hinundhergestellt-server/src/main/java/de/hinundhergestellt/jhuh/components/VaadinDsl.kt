package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField

fun HasComponents.button(label: String? = null, block: Button.() -> Unit = {}) = init(Button(label), block)
fun HasComponents.button(icon: VaadinIcon, block: Button.() -> Unit = {}) = init(Button(icon.create()), block)
fun HasComponents.textArea(label: String? = null, block: TextArea.() -> Unit = {}) = init(TextArea(label), block)
fun HasComponents.textField(label: String? = null, block: TextField.() -> Unit = {}) = init(TextField(label), block)
fun HasComponents.verticalLayout(block: VerticalLayout.() -> Unit = {}) = init(VerticalLayout(), block)

fun Dialog.header(block: Dialog.DialogHeader.() -> Unit) = header.block()
fun Dialog.footer(block: Dialog.DialogFooter.() -> Unit) = footer.block()

private fun <T: Component> HasComponents.init(component: T, block: T.() -> Unit = {}) =
    component.also { add(it); it.block() }