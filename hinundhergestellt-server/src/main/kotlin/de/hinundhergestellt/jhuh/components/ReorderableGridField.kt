@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.customfield.CustomField
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.grid.dnd.GridDropLocation
import com.vaadin.flow.component.grid.dnd.GridDropMode
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.data.provider.ListDataProvider
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.optionals.getOrNull

class ReorderableGridField(
    label: String
) : CustomField<List<ReorderableGridField.Item>>() {

    private val items = mutableListOf<Item>()
    private val provider = ListDataProvider(items)

    private val itemsGrid: Grid<Item>

    init {
        classNames += "label-actions"

        add(*components {
            horizontalLayout {
                isSpacing = true
                isPadding = false
                alignItems = FlexComponent.Alignment.CENTER
                justifyContentMode = FlexComponent.JustifyContentMode.BETWEEN
                setWidthFull()
                element.setAttribute("slot", "label")

                nativeLabel(label)
                button(VaadinIcon.PLUS) {
                    addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON)
                    addClickListener { addItem() }
                }
            }
            verticalLayout {
                isPadding = false
                isSpacing = false

                itemsGrid = grid {
                    emptyStateText = "Keine Einträge vorhanden."
                    setSizeFull()
                    dataProvider = provider
                    addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT)

                    val binder = binder<Item>()
                    editor.binder = binder
                    editor.isBuffered = false

                    textColumn(Item::name) {
                        flexGrow = 1
                        editorComponent = root { textField { setWidthFull(); bind(binder).toProperty(Item::name) } }
                    }
                    textColumn(Item::value) {
                        flexGrow = 2
                        editorComponent = root { textField { setWidthFull(); bind(binder).toProperty(Item::value) } }
                    }
                    componentColumn(::itemRemoveButton) {
                        isAutoWidth = true
                        flexGrow = 0
                    }

                    addItemDoubleClickListener { editor.editItem(it.item) }
                    addItemClickListener { if (editor.isOpen && it.item != editor.item) editor.cancel() }

                    isRowsDraggable = true
                    dropMode = GridDropMode.BETWEEN

                    var draggedItem: Item? = null
                    addDragStartListener { draggedItem = it.draggedItems[0] }
                    addDragEndListener { draggedItem = null }
                    addDropListener {
                        val from = items.indexOf(draggedItem!!)
                        if (from == -1) return@addDropListener
                        var to = it.dropTargetItem.getOrNull()?.let { item -> items.indexOf(item) } ?: items.size
                        if (it.dropLocation == GridDropLocation.BELOW) to += 1
                        val boundedTo = to.coerceIn(0, items.size)
                        if (from == boundedTo || from == boundedTo - 1) return@addDropListener

                        val moved = items.removeAt(from)
                        items.add(if (boundedTo > from) boundedTo - 1 else boundedTo, moved)
                        provider.refreshAll()
                        updateValue()
                    }
                }
            }
        })
    }

    override fun getHeight(): String? = itemsGrid.height
    override fun setHeight(height: String?) {
        itemsGrid.height = height
    }

    override fun generateModelValue() = items

    override fun setPresentationValue(newItems: List<Item>) {
        items.clear()
        newItems.forEach { items += Item(it.name, it.value) }
        provider.refreshAll()
        itemsGrid.recalculateColumnWidths()
    }

    private fun addItem() {
        items += Item("", "")
        provider.refreshAll()
        itemsGrid.editor.editItem(items.last())
        itemsGrid.scrollToEnd()
    }

    private fun removeItem(item: Item) {
        items -= item
        provider.refreshAll()
        updateValue()
    }

    private fun itemRemoveButton(item: Item) =
        root {
            button(VaadinIcon.TRASH) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON)
                addClickListener { removeItem(item) }
            }
        }

    class Item(
        var name: String,
        var value: String
    )
}

@VaadinDsl
fun (@VaadinDsl HasComponents).reorderableGridField(
    label: String,
    block: (@VaadinDsl ReorderableGridField).() -> Unit
): ReorderableGridField {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(ReorderableGridField(label), block)
}