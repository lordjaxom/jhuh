package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.ComponentEventListener
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.ClientItemToggleEvent
import com.vaadin.flow.component.grid.ColumnTextAlign
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridMultiSelectionModel
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.dom.Style
import kotlin.math.abs
import kotlin.math.min
import kotlin.streams.asSequence

fun <T, V> Grid<T>.textColumn(header: String, flexGrow: Int = 1, valueProvider: (T) -> V): Grid.Column<T> =
    addColumn(valueProvider)
        .setHeader(header)
        .also {
            it.isSortable = false
            it.flexGrow = flexGrow
        }

fun <T, V> Grid<T>.countColumn(header: String = "#", valueProvider: (T) -> V): Grid.Column<T> =
    addColumn(valueProvider)
        .setHeader(header)
        .also {
            it.isSortable = false
            it.textAlign = ColumnTextAlign.END
            it.width = "4em"
            it.flexGrow = 0
        }

fun <T> Grid<T>.actionsColumn(count: Int, actionsProvider: (T) -> List<Button>): Grid.Column<T> =
    addComponentColumn { buildActionsLayout(count, actionsProvider(it)) }
        .setHeader("")
        .also {
            it.isSortable = false
            it.width = "${count * 30 + 16}px"
            it.flexGrow = 0
        }

fun <T> Grid<T>.actionsColumn(actionsProvider: (T) -> Button): Grid.Column<T> =
    actionsColumn(1) { listOf(actionsProvider(it)) }

fun <T> Grid<T>.iconColumn(iconProvider: (T) -> Icon): Grid.Column<T> =
    addComponentColumn(iconProvider)
        .setHeader("")
        .apply {
            isSortable = false
            width = "32px"
            flexGrow = 0
        }

fun <T, V : Component> TreeGrid<T>.hierarchyComponentColumn(header: String, flexGrow: Int, componentProvider: (T) -> V): Grid.Column<T> =
    addComponentHierarchyColumn(componentProvider)
        .setHeader(header)
        .also {
            it.isSortable = false
            it.flexGrow = flexGrow
        }

fun <T, V> TreeGrid<T>.hierarchyTextColumn(header: String, flexGrow: Int, valueProvider: (T) -> V): Grid.Column<T> =
    addHierarchyColumn(valueProvider)
        .setHeader(header)
        .also {
            it.isSortable = false
            it.flexGrow = flexGrow
        }

inline fun <reified T> Grid<T>.rangeMultiSelectionMode() {
    val selectionModel = setSelectionMode(Grid.SelectionMode.MULTI) as GridMultiSelectionModel<T>
    selectionModel.addClientItemToggleListener(object : ComponentEventListener<ClientItemToggleEvent<T>> {
        private var rangeStartItem: T? = null
        override fun onComponentEvent(event: ClientItemToggleEvent<T>) {
            if (rangeStartItem == null) rangeStartItem = event.item
            if (event.isShiftKey) {
                val rangeStart = listDataView.getItemIndex(rangeStartItem).get()
                val rangeEnd = listDataView.getItemIndex(event.item).get()
                val rangeItems = listDataView.items.asSequence()
                    .drop(min(rangeStart, rangeEnd))
                    .take(abs(rangeStart - rangeEnd))
                    .toList().toTypedArray()
                if (event.isSelected) selectionModel.selectItems(*rangeItems)
                else selectionModel.deselectItems(*rangeItems)
            }
            rangeStartItem = event.item
        }
    })
}

private fun buildActionsLayout(count: Int, components: List<Button>) =
    HorizontalLayout().apply {
        isSpacing = false
        width = "${count * 30}px"
        justifyContentMode = JustifyContentMode.END
        style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
        add(components)
    }