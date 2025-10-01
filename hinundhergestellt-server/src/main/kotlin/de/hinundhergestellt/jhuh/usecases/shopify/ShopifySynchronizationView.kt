package de.hinundhergestellt.jhuh.usecases.shopify

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.CustomIcon
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.ellipsisColumn
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.html.ellipsisSpan
import de.hinundhergestellt.jhuh.components.progressOverlay
import de.hinundhergestellt.jhuh.components.root
import de.hinundhergestellt.jhuh.components.span
import de.hinundhergestellt.jhuh.components.text
import de.hinundhergestellt.jhuh.components.treegrid.hierarchyComponentColumn
import de.hinundhergestellt.jhuh.components.treegrid.recursiveSelectTreeGrid
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.Item
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.ProductHeaderItem
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.VariantHeaderItem
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
        itemsGrid = recursiveSelectTreeGrid<Item> {
            emptyStateText = "Keine abweichenden Einträge gefunden"
            hierarchyComponentColumn({ treeItemLabel(it) }) {
                setHeader(" ")
            }
            selectionMode = Grid.SelectionMode.MULTI
            setSizeFull()
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
            addSelectionListener { validateActions() }

            setItems(service.items, Item::children)
        }
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
                service.synchronize(::report)
            }
            itemsGrid.setItems(service.items, Item::children)
            itemsGrid.recalculateColumnWidths()
        }
    }

    private fun validateActions() {
        applyButton.isEnabled = itemsGrid.selectedItems.isNotEmpty()
    }

    private fun treeItemLabel(item: Item) =
        root {
            horizontalLayout {
                alignItems = FlexComponent.Alignment.CENTER
                style["gap"] = "var(--lumo-space-s)"

                val icon = when (item) {
                    is ProductHeaderItem -> CustomIcon.PRODUCT.create()
                    is VariantHeaderItem -> CustomIcon.VARIATION.create()
                    else -> VaadinIcon.REFRESH.create()
                }
                span { add(icon.apply { setSize("var(--lumo-icon-size-s)"); color = "var(--lumo-secondary-text-color)" }) }
                ellipsisSpan(item.message) {}
            }
        }
}
