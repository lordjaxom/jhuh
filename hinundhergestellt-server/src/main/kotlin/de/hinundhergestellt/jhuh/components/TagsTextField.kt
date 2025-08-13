@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.customfield.CustomField
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.dom.Style
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class TagsTextField(label: String? = null) : CustomField<Set<String>>() {

    private val input: TextField
    private val container: Div

    private val tags = LinkedHashSet<String>()

    init {
        add(root {
            verticalLayout {
                isPadding = false
                isSpacing = false
                defaultHorizontalComponentAlignment = FlexComponent.Alignment.STRETCH

                input = textField(label) {
                    setWidthFull()
                    addKeyDownListener(Key.ENTER, { tryAddFromInput() })
                }
                container = div {
                    style.setDisplay(Style.Display.FLEX)
                    style.setFlexWrap(Style.FlexWrap.WRAP)
                    style.setMarginTop("var(--lumo-space-s)")
                    style.set("gap", "var(--lumo-space-s)")
                }
            }
        })
    }

    fun addTags(tags: Collection<String>) {
        tags.forEach { addTag(it, true) }
        updateValue()
    }

    override fun setReadOnly(readOnly: Boolean) {
        super.setReadOnly(readOnly)
        if (readOnly) {
            input.classNames += "label-only"
            container.style.remove("margin-top")
        } else {
            input.classNames -= "label-only"
            container.style.setMarginTop("var(--lumo-space-s)")
        }
        setPresentationValue(tags.toMutableSet())
    }

    override fun generateModelValue() = tags.toSet()

    override fun setPresentationValue(tags: Set<String>) {
        this.tags.clear()
        container.removeAll()
        tags.forEach { addTag(it, false) }
    }

    private fun addTag(value: String, isNew: Boolean) {
        if (!tags.add(value)) return
        container.add(makeBadge(value, isNew))
    }

    private fun removeTag(value: String) {
        if (!tags.remove(value)) return
        container.remove(container.children.filter { it.element.getProperty("datasetTag") == value }.toList())
        updateValue()
    }

    private fun tryAddFromInput() {
        val value = input.value.trim()
        if (value.isEmpty()) return

        addTag(value, true)
        input.clear()
        updateValue()
    }

    private fun makeBadge(value: String, isNew: Boolean) =
        root {
            div {
                style.setDisplay(Style.Display.INLINE_FLEX)
                style.setAlignItems(Style.AlignItems.CENTER)
                style.setFontSize("var(--lumo-font-size-xs)")
                style.setBackgroundColor(
                    if (isNew) "var(--lumo-success-color-10pct)"
                    else "var(--lumo-contrast-10pct)"
                )
                style.setBorderRadius("var(--lumo-border-radius-l)")
                style.setHeight("var(--lumo-size-xs)")
                style.setPadding("0 var(--lumo-space-s)")
                element.setProperty("datasetTag", value)

                span(value)
                if (!isReadOnly) {
                    button(VaadinIcon.CLOSE_SMALL) {
                        addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_CONTRAST)
                        addClickListener { removeTag(value) }
                    }
                }
            }
        }
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).tagsTextField(label: String? = null, block: (@VaadinDsl TagsTextField).() -> Unit = {}): TagsTextField {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(TagsTextField(label), block)
}
