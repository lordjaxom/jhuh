@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components.treegrid

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.treegrid.TreeGrid
import de.hinundhergestellt.jhuh.components.VaadinDsl
import de.hinundhergestellt.jhuh.components.init
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@VaadinDsl
inline fun <T> (@VaadinDsl HasComponents).treeGrid(block: (@VaadinDsl TreeGrid<T>).() -> Unit = {}): TreeGrid<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(TreeGrid<T>(), block)
}

@VaadinDsl
fun <T> TreeGrid<T>.hierarchyComponentColumn(
    componentProvider: (T) -> Component,
    block: (@VaadinDsl Grid.Column<T>).() -> Unit = {}
): Grid.Column<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return addComponentHierarchyColumn(componentProvider).apply { isSortable = false; block() }
}
