@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components.treegrid

import com.vaadin.flow.component.ComponentEvent
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.grid.AbstractGridMultiSelectionModel
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridSelectionModel
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery
import com.vaadin.flow.data.selection.SelectionEvent
import de.hinundhergestellt.jhuh.components.VaadinDsl
import de.hinundhergestellt.jhuh.components.init
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.streams.asSequence

class RecursiveSelectTreeGrid<T> : TreeGrid<T>() {

    override fun setSelectionMode(selectionMode: SelectionMode): GridSelectionModel<T> {
        if (SelectionMode.MULTI != selectionMode) {
            return super.setSelectionMode(selectionMode)
        }

        val model: GridSelectionModel<T> = object : AbstractGridMultiSelectionModel<T>(this) {
            protected override fun fireSelectionEvent(event: SelectionEvent<Grid<T>, T>) {
                this@RecursiveSelectTreeGrid.fireEvent(event as ComponentEvent<*>)
            }

            override fun selectFromClient(item: T) {
                updateSelection(getChildrenRecursively(item).toSet(), setOf())
            }

            override fun deselectFromClient(item: T) {
                updateSelection(setOf(), getChildrenRecursively(item).toSet())
            }
        }
        setSelectionModel(model, selectionMode)
        return model
    }

    private fun getChildrenRecursively(items: Sequence<T>): Sequence<T> =
        items.flatMap { getChildrenRecursively(it) }

    private fun getChildrenRecursively(item: T) =
        sequenceOf(item) + getChildrenRecursively(dataProvider.fetchChildren(HierarchicalQuery(null, item)).asSequence())
}

@VaadinDsl
inline fun <T> (@VaadinDsl HasComponents).recursiveSelectTreeGrid(block: (@VaadinDsl TreeGrid<T>).() -> Unit = {}): TreeGrid<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(RecursiveSelectTreeGrid(), block)
}
