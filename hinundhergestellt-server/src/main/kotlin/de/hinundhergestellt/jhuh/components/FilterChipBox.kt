@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.ItemLabelGenerator
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.CheckboxGroup
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant
import com.vaadin.flow.component.contextmenu.ContextMenu
import com.vaadin.flow.component.customfield.CustomField
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.dom.Style
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class FilterChipBox<T>(
    private val filterName: String,
) : CustomField<Set<T>>() {

    private val selected = linkedSetOf<T>()

    private val mainButton: Button
    private val clearButton: Button
    private val checkboxGroup: CheckboxGroup<T>

    init {
        add(root {
            div {
                style.setDisplay(Style.Display.INLINE_FLEX)
                style.setAlignItems(Style.AlignItems.CENTER)
                style.setFontSize("var(--lumo-font-size-xs)")
                style.setBorderRadius("var(--lumo-border-radius-l)")
                style.setBackgroundColor("var(--lumo-contrast-10pct)")
                style.setPadding("0 var(--lumo-space-s)")
                style.setHeight("var(--lumo-size-xs)")
                style.set("gap", "var(--lumo-space-xs)")

                mainButton = button {
                    isIconAfterText = true
                    addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL)

                    ContextMenu().apply {
                        isOpenOnClick = true
                        target = this@button

                        val menuItem = addItem(root {
                            verticalLayout {
                                isPadding = false
                                isSpacing = false
                                checkboxGroup = checkboxGroup {
                                    addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL)
                                    addValueChangeListener { changeSelection(it.value) }
                                }
                            }
                        })
                        menuItem.element.addEventListener("click", {}).stopPropagation()
                    }
                }
                clearButton = button(VaadinIcon.CLOSE_SMALL) {
                    isVisible = false
                    addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON)
                    addClickListener { clearSelection() }
                    element.addEventListener("click", {}).stopPropagation()
                }
            }
        })

        clearSelection()
    }

    fun setItems(items: Collection<T>) {
        checkboxGroup.setItems(items)
    }

    fun itemLabelGenerator(labelProvider: (T) -> String) {
        checkboxGroup.itemLabelGenerator = ItemLabelGenerator { labelProvider(it) }
    }

    override fun generateModelValue() = selected.toSet()
    override fun setPresentationValue(newValue: Set<T>) {
        selected.clear()
        selected.addAll(newValue)
        updateChipText()
        updateValue()
    }

    private fun updateChipText() {
        val selectedText = selected
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = ", ", prefix = ": ") { checkboxGroup.itemLabelGenerator.apply(it) }
            ?: ""
        mainButton.text = filterName + selectedText
        mainButton.icon = VaadinIcon.CHEVRON_DOWN_SMALL.takeIf { selected.isEmpty() }?.create()
        clearButton.isVisible = selected.isNotEmpty()
    }

    private fun changeSelection(value: Set<T>) {
        selected.clear()
        selected += value
        updateChipText()
        updateValue()
    }

    private fun clearSelection() {
        selected.clear()
        checkboxGroup.deselectAll()
        updateChipText()
        updateValue()
    }
}

@VaadinDsl
inline fun <T> (@VaadinDsl HasComponents).filterChipBox(
    filterName: String,
    block: (@VaadinDsl FilterChipBox<T>).() -> Unit = {}
): FilterChipBox<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(FilterChipBox(filterName), block)
}