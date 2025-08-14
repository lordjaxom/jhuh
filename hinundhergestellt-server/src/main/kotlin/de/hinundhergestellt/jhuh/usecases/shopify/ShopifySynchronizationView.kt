package de.hinundhergestellt.jhuh.usecases.shopify

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.CustomIcon
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.hierarchyComponentColumn
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.progressOverlay
import de.hinundhergestellt.jhuh.components.root
import de.hinundhergestellt.jhuh.components.span
import de.hinundhergestellt.jhuh.components.text
import de.hinundhergestellt.jhuh.components.textColumn
import de.hinundhergestellt.jhuh.components.treeGrid
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.Item
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.ProductItem
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.VariantItem
import kotlinx.coroutines.CoroutineScope

@Route
@PageTitle("Shopify-Synchronisation")
class ShopifySynchronizationView(
    private val service: ShopifySynchronizationService,
    applicationScope: CoroutineScope
) : VerticalLayout() {

    private val applyButton: Button
    private val itemsGrid: TreeGrid<Item>
    private val progressOverlay = progressOverlay()

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
            selectionMode = Grid.SelectionMode.MULTI
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
            itemsGrid.setItems(service.items, Item::children)
            itemsGrid.recalculateColumnWidths()
        }
    }

    private fun applySelectedItems() {
        vaadinScope.launchWithReporting {
            application {
                service.apply(itemsGrid.selectedItems, ::report)
                service.refresh(::report)
            }
            itemsGrid.setItems(service.items, Item::children)
        }
    }

    private fun validateActions() {
        applyButton.isEnabled = itemsGrid.selectedItems.isNotEmpty()
    }

    private fun treeItemLabel(item: Item) =
        root {
            horizontalLayout {
                alignItems = FlexComponent.Alignment.CENTER
                style.set("gap", "var(--lumo-space-xs)")

                val icon = when (item) {
                    is ProductItem -> CustomIcon.PRODUCT
                    is VariantItem -> CustomIcon.VARIATION
                }
                span { add(icon.create().apply { setSize("var(--lumo-icon-size-s)"); color = "var(--lumo-secondary-text-color)" }) }
                text(item.title)
            }
        }
}
