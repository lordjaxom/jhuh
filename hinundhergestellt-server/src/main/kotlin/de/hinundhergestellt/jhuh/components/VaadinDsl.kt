@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.ItemLabelGenerator
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.IntegerField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.treegrid.TreeGrid
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun HasComponents.button(text: String? = null, block: Button.() -> Unit = {}): Button {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Button(text), block)
}

inline fun HasComponents.button(icon: VaadinIcon, block: Button.() -> Unit = {}): Button {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Button(icon.create()), block)
}

inline fun HasComponents.checkbox(label: String? = null, block: Checkbox.() -> Unit = {}): Checkbox {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Checkbox(label), block)
}

inline fun <T> HasComponents.comboBox(label: String? = null, block: ComboBox<T>.() -> Unit = {}): ComboBox<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(ComboBox(label), block)
}

inline fun HasComponents.div(block: Div.() -> Unit = {}): Div {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Div(), block)
}

inline fun <T> HasComponents.grid(block: Grid<T>.() -> Unit = {}): Grid<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Grid(), block)
}

inline fun HasComponents.horizontalLayout(block: HorizontalLayout.() -> Unit = {}): HorizontalLayout {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(HorizontalLayout(), block)
}

inline fun HasComponents.integerField(label: String? = null, block: IntegerField.() -> Unit = {}): IntegerField {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(IntegerField(label), block)
}

inline fun HasComponents.textArea(label: String? = null, block: TextArea.() -> Unit = {}): TextArea {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(TextArea(label), block)
}

inline fun HasComponents.textField(label: String? = null, block: TextField.() -> Unit = {}): TextField {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(TextField(label), block)
}

inline fun <T> HasComponents.treeGrid(block: TreeGrid<T>.() -> Unit = {}): TreeGrid<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(TreeGrid<T>(), block)
}

inline fun HasComponents.verticalLayout(block: VerticalLayout.() -> Unit = {}): VerticalLayout {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(VerticalLayout(), block)
}

fun <T> ComboBox<T>.itemLabelGenerator(valueProvider: (T) -> String) {
    itemLabelGenerator = ItemLabelGenerator { valueProvider(it) }
}

inline fun Dialog.header(block: Dialog.DialogHeader.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    header.block()
}

inline fun Dialog.footer(block: Dialog.DialogFooter.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    footer.block()
}

inline fun <T : Component> HasComponents.init(component: T, block: T.() -> Unit = {}): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return component.also { add(it); it.block() }
}