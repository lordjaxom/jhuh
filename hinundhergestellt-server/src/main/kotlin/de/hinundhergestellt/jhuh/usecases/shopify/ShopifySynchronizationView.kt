package de.hinundhergestellt.jhuh.usecases.shopify

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.CustomIcon
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.hierarchyComponentColumn
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.progressOverlay
import de.hinundhergestellt.jhuh.components.rangeMultiSelectionMode
import de.hinundhergestellt.jhuh.components.root
import de.hinundhergestellt.jhuh.components.span
import de.hinundhergestellt.jhuh.components.text
import de.hinundhergestellt.jhuh.components.textColumn
import de.hinundhergestellt.jhuh.components.treeGrid
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.Item
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.Type
import kotlinx.coroutines.CoroutineScope
import java.util.stream.Stream
import kotlin.streams.asStream

@Route
@PageTitle("Shopify-Synchronisation")
class ShopifySynchronizationView(
    private val service: ShopifySynchronizationService,
    applicationScope: CoroutineScope
) : VerticalLayout() {

    private val applyButton: Button
    private val itemsGrid: TreeGrid<Item>
    private val progressOverlay = progressOverlay()

    private val treeDataProvider = TreeDataProvider()

    private val vaadinScope = VaadinCoroutineScope(this, applicationScope, progressOverlay)

    init {
        setHeightFull()
        width = "1170px"
        themeList -= "spacing"
        style.setMargin("0 auto")

        horizontalLayout {
            justifyContentMode = FlexComponent.JustifyContentMode.END
            setWidthFull()

            button("Aktualisieren") {
                addClickListener { refresh() }
            }
            applyButton = button("Markierte übernehmen") {
                isEnabled = false
                addClickListener { applySelectedItems() }
            }
        }
        itemsGrid = treeGrid<Item> {
            emptyStateText = "Keine abweichenden Einträge gefunden"
            hierarchyComponentColumn({ treeItemLabel(it) }) {
                setHeader("Bezeichnung")
                flexGrow = 1
            }
            textColumn({ it.message }) {
                setHeader("Sachverhalt")
                flexGrow = 1
            }
            rangeMultiSelectionMode()
            setDataProvider(treeDataProvider)
//            expandRecursively(treeDataProvider.fetchChildren(HierarchicalQuery(null, null)), Int.MAX_VALUE)
            setSizeFull()
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
            addSelectionListener { validateActions() }
        }
//
//        val refreshHandler: () -> Unit = { vaadinScope.launch { treeDataProvider.refreshAll(); progressOverlay.isVisible = false } }
//        addAttachListener { service.refreshListeners += refreshHandler }
//        addDetachListener { service.refreshListeners -= refreshHandler }
    }

    private fun refresh() {
        vaadinScope.launchWithReporting {
            application { service.refresh(::report) }
            treeDataProvider.refreshAll()
            itemsGrid.recalculateColumnWidths()
        }
    }

    private fun applySelectedItems() {
        vaadinScope.launchWithReporting {
            application {
                service.apply(itemsGrid.selectedItems, ::report)
                service.refresh(::report)
            }
            treeDataProvider.refreshAll()
        }
    }
//
//    private fun synchronize() = vaadinScope.launch {
//        progressOverlay.text = "Synchronisiere Produkte mit Shopify..."
//        progressOverlay.isVisible = true
//        try {
//            withContext(applicationScope.coroutineContext) {
//                service.synchronize {
//                    vaadinScope.launch { progressOverlay.text = it }
//                }
//            }
//        } catch (e: Throwable) {
//            showErrorNotification(e)
//        } finally {
//            progressOverlay.isVisible = false
//            treeDataProvider.refreshAll()
//        }
//    }

    private fun validateActions() {
        applyButton.isEnabled = itemsGrid.selectedItems.isNotEmpty()
    }

    private fun treeItemLabel(item: Item) =
        root {
            horizontalLayout {
                alignItems = FlexComponent.Alignment.CENTER
                style.set("gap", "var(--lumo-space-xs)")

                val icon = when (item.type) {
                    Type.PRODUCT -> CustomIcon.PRODUCT
                    Type.VARIANT -> CustomIcon.VARIATION
                }
                span { add(icon.create().apply { setSize("var(--lumo-icon-size-s)"); color = "var(--lumo-secondary-text-color)" }) }
                text(item.title)
            }
        }

//
//    private fun syncableItemStatus(item: SyncableItem): Icon =
//        if (item !is ProductItem) Icon()
//        else {
//            val problems = item.syncProblems
//            when {
//                problems.has<Error>() -> VaadinIcon.WARNING.create().apply { style.setColor("var(--lumo-error-color") }
//                problems.isNotEmpty() -> VaadinIcon.WARNING.create().apply { style.setColor("var(--lumo-warning-color") }
//                item.isMarkedForSync -> VaadinIcon.CHECK.create().apply { style.setColor("var(--lumo-success-color") }
//                else -> VaadinIcon.CIRCLE.create().apply { style.setColor("lightgrey") }
//            }.also {
//                it.setSize("16px")
//                if (problems.isNotEmpty()) {
//                    Tooltip.forComponent(it).withText(problems.joinToString("\n"))
//                }
//            }
//        }
//
//    private fun syncableItemActions(item: SyncableItem) =
//        buildList {
//            if (item is ProductItem) {
//                val markUnmarkButton =
//                    if (item.isMarkedForSync) GridActionButton(VaadinIcon.TRASH) { unmarkItem(item) }
//                    else GridActionButton(VaadinIcon.PLUS) { markItem(item) }.apply { isEnabled = !item.syncProblems.has<Error>() }
//                add(markUnmarkButton)
//            }
//        }

    private inner class TreeDataProvider : AbstractBackEndHierarchicalDataProvider<Item, Void?>() {

        override fun fetchChildrenFromBackEnd(query: HierarchicalQuery<Item, Void?>): Stream<Item> {
            if (query.parent != null) return Stream.empty()
            return service.items.asSequence()
                .drop(query.offset)
                .take(query.limit)
                .asStream()
        }

        override fun hasChildren(item: Item) = false
        override fun getChildCount(query: HierarchicalQuery<Item, Void?>) = fetchChildren(query).count().toInt()
    }
}
