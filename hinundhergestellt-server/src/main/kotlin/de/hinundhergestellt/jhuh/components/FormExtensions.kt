@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.html.Span
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@VaadinDsl
fun (@VaadinDsl FormLayout).header(text: String) {
    add(Span(text).apply {
        this.style.setColor("var(--lumo-secondary-text-color)")
        this.style.setFontSize("var(--lumo-font-size-l)")
        this.style.setFontWeight(500)
    })
}

@VaadinDsl
inline fun (@VaadinDsl FormLayout).row(block: (@VaadinDsl FormLayout.FormRow).() -> Unit): FormLayout.FormRow {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(FormLayout.FormRow(), block)
}

@VaadinDsl
inline fun <T: Component> (@VaadinDsl FormLayout.FormRow).column(colspan: Int, block: (@VaadinDsl HasComponents).() -> T) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    add(HasSingleComponent().block(), colspan)
}