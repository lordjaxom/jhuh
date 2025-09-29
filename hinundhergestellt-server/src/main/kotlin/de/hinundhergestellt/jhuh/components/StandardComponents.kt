@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.html.Span
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@VaadinDsl
fun (@VaadinDsl HasComponents).lightHeaderSpan(text: String, block: (@VaadinDsl Span).() -> Unit = {}): Span {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    val span = Span(text).apply {
        style.setMarginTop("var(--lumo-space-m)")
        style.setColor("var(--lumo-secondary-text-color)")
        style.setFontSize("var(--lumo-font-size-l)")
        style.setFontWeight(500)
    }
    return init(span, block)
}
