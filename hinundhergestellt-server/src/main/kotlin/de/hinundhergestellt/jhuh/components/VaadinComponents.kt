@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.Text
import com.vaadin.flow.component.accordion.Accordion
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.html.NativeLabel
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.BigDecimalField
import com.vaadin.flow.component.textfield.IntegerField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.treegrid.TreeGrid
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@VaadinDsl
inline fun (@VaadinDsl HasComponents).accordion(block: (@VaadinDsl Accordion).() -> Unit = {}): Accordion {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Accordion(), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).bigDecimalField(label: String? = null, block: (@VaadinDsl BigDecimalField).() -> Unit = {}): BigDecimalField {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(BigDecimalField(label), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).button(text: String? = null, block: (@VaadinDsl Button).() -> Unit = {}): Button {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Button(text), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).button(icon: VaadinIcon, block: (@VaadinDsl Button).() -> Unit = {}): Button {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Button(icon.create()), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).checkbox(label: String? = null, block: (@VaadinDsl Checkbox).() -> Unit = {}): Checkbox {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Checkbox(label), block)
}

@VaadinDsl
inline fun <T> (@VaadinDsl HasComponents).comboBox(label: String? = null, block: (@VaadinDsl ComboBox<T>).() -> Unit = {}): ComboBox<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(ComboBox(label), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).div(block: (@VaadinDsl Div).() -> Unit = {}): Div {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Div(), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).formLayout(block: (@VaadinDsl FormLayout).() -> Unit = {}): FormLayout {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(FormLayout(), block)
}

@VaadinDsl
inline fun <T> (@VaadinDsl HasComponents).grid(block: (@VaadinDsl Grid<T>).() -> Unit = {}): Grid<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Grid(), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).h1(text: String, block: (@VaadinDsl H1).() -> Unit = {}): H1 {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(H1(text), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).horizontalLayout(block: (@VaadinDsl HorizontalLayout).() -> Unit = {}): HorizontalLayout {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(HorizontalLayout(), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).integerField(label: String? = null, block: (@VaadinDsl IntegerField).() -> Unit = {}): IntegerField {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(IntegerField(label), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).nativeLabel(label: String, block: (@VaadinDsl NativeLabel).() -> Unit = {}): NativeLabel {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(NativeLabel(label), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).span(text: String, block: (@VaadinDsl Span).() -> Unit = {}): Span {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Span(text), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).text(text: String, block: (@VaadinDsl Text).() -> Unit = {}): Text {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(Text(text), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).textArea(label: String? = null, block: (@VaadinDsl TextArea).() -> Unit = {}): TextArea {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(TextArea(label), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).textField(label: String? = null, block: (@VaadinDsl TextField).() -> Unit = {}): TextField {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(TextField(label), block)
}

@VaadinDsl
inline fun <T> (@VaadinDsl HasComponents).treeGrid(block: (@VaadinDsl TreeGrid<T>).() -> Unit = {}): TreeGrid<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(TreeGrid<T>(), block)
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).verticalLayout(block: (@VaadinDsl VerticalLayout).() -> Unit = {}): VerticalLayout {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(VerticalLayout(), block)
}
