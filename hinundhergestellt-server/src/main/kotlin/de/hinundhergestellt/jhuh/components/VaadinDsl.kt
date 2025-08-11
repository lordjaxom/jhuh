@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.ItemLabelGenerator
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.customfield.CustomField
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.dom.Element
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
@DslMarker
annotation class VaadinDsl

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

@VaadinDsl
inline fun <T : Component> (@VaadinDsl HasComponents).init(component: T, block: (@VaadinDsl T).() -> Unit = {}): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return component.also { add(it); it.block() }
}

@VaadinDsl
abstract class DummyHasComponents: HasComponents {
    abstract override fun add(vararg components: Component)

    override fun add(components: Collection<Component>) = throw UnsupportedOperationException("add")
    override fun getElement(): Element = throw UnsupportedOperationException("element")
}

@VaadinDsl
class HasSingleComponent : DummyHasComponents() {
    private var initialized = false

    override fun add(vararg components: Component) {
        require(components.size == 1 && !initialized) { "Can only host single component" }
        initialized = true
    }
}

@VaadinDsl
inline fun <reified T: Component> single(block: (@VaadinDsl HasComponents).() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return HasSingleComponent().block()
}