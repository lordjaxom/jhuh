package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.ColumnTextAlign
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.dom.Style

fun <T, V> Grid<T>.addTextColumn(header: String, flexGrow: Int, valueProvider: (T) -> V): Grid.Column<T> =
    addColumn(valueProvider)
        .setHeader(header)
        .also {
            it.isSortable = false
            it.flexGrow = flexGrow
        }

fun <T, V> Grid<T>.addCountColumn(header: String = "#", valueProvider: (T) -> V): Grid.Column<T> =
    addColumn(valueProvider)
        .setHeader(header)
        .also {
            it.isSortable = false
            it.textAlign = ColumnTextAlign.END
            it.width = "4em"
            it.flexGrow = 0
        }

fun <T> Grid<T>.addActionsColumn(count: Int, actionsProvider: (T) -> List<Button>): Grid.Column<T> =
    addComponentColumn { buildActionsLayout(count, actionsProvider(it)) }
        .setHeader("")
        .also {
            it.isSortable = false
            it.width = "${count * 30 + 16}px"
            it.flexGrow = 0
        }

fun <T> Grid<T>.addActionsColumn(actionsProvider: (T) -> Button): Grid.Column<T> =
    addActionsColumn(1) { listOf(actionsProvider(it)) }

fun <T> Grid<T>.addIconColumn(iconProvider: (T) -> Icon): Grid.Column<T> =
    addComponentColumn(iconProvider)
        .setHeader("")
        .apply {
            isSortable = false
            width = "32px"
            flexGrow = 0
        }

fun <T, V> TreeGrid<T>.addHierarchyTextColumn(header: String, flexGrow: Int, valueProvider: (T) -> V): Grid.Column<T> =
    addHierarchyColumn(valueProvider)
        .setHeader(header)
        .also {
            it.isSortable = false
            it.flexGrow = flexGrow
        }

private fun buildActionsLayout(count: Int, components: List<Button>) =
    HorizontalLayout().apply {
        isSpacing = false
        width = "${count * 30}px"
        justifyContentMode = JustifyContentMode.END
        style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
        add(components)
    }