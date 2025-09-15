package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.contextmenu.ContextMenu
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.html.Hr
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import com.vaadin.flow.router.RouteAlias
import de.hinundhergestellt.jhuh.backend.mapping.hasErrors
import de.hinundhergestellt.jhuh.backend.mapping.toPresentationString
import de.hinundhergestellt.jhuh.components.Article
import de.hinundhergestellt.jhuh.components.CustomIcon
import de.hinundhergestellt.jhuh.components.FilterChipBox
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.componentColumn
import de.hinundhergestellt.jhuh.components.filterChipBox
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.progressOverlay
import de.hinundhergestellt.jhuh.components.root
import de.hinundhergestellt.jhuh.components.span
import de.hinundhergestellt.jhuh.components.textColumn
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.treegrid.hierarchyComponentColumn
import de.hinundhergestellt.jhuh.components.treegrid.treeGrid
import de.hinundhergestellt.jhuh.usecases.labels.LabelGeneratorService
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.CategoryItem
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.Item
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.ProductItem
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.VariationItem
import kotlinx.coroutines.CoroutineScope
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream

@Route
@RouteAlias("")
@PageTitle("Produktverwaltung")
class ProductManagerView(
    private val service: ProductManagerService,
    private val editCategoryDialogFactory: EditCategoryDialogFactory,
    private val editProductDialogFactory: EditProductDialogFactory,
    private val labelService: LabelGeneratorService,
    applicationScope: CoroutineScope
) : VerticalLayout() {

    private val filterTextField: TextField
    private val markedForSyncFilterChipBox: FilterChipBox<Boolean>
    private val hasProblemsFilterChipBox: FilterChipBox<Boolean>
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
        }
        horizontalLayout {
            alignItems = FlexComponent.Alignment.CENTER
            setWidthFull()

            filterTextField = textField {
                placeholder = "Suche..."
                prefixComponent = VaadinIcon.SEARCH.create()
                isClearButtonVisible = true
                setWidthFull()
                addValueChangeListener { treeDataProvider.refreshAll() }
            }
        }
        horizontalLayout {
            markedForSyncFilterChipBox = filterChipBox<Boolean>("Synchronisiert") {
                setItems(listOf(true, false))
                itemLabelGenerator { if (it) "Ja" else "Nein" }
                value = setOf(true)
                addValueChangeListener { treeDataProvider.refreshAll() }
            }
            hasProblemsFilterChipBox = filterChipBox("Fehlerhaft") {
                setItems(listOf(true, false))
                itemLabelGenerator { if (it) "Ja" else "Nein" }
                addValueChangeListener { treeDataProvider.refreshAll() }
            }
        }
        itemsGrid = treeGrid<Item> {
            hierarchyComponentColumn({ treeItemLabel(it) }) {
                setHeader("Bezeichnung")
                flexGrow = 100
            }
            textColumn({ it.vendor?.name }) {
                setHeader("Hersteller")
                flexGrow = 1
            }
            textColumn({ it.type }) {
                setHeader("Produktart")
                flexGrow = 1
            }
            componentColumn({ treeItemStatus(it) }) {
                isAutoWidth = true
                flexGrow = 0
            }
            componentColumn({ treeItemActions(it) }) {
                isAutoWidth = true
                flexGrow = 0
            }
            setDataProvider(treeDataProvider)
            expand(treeDataProvider.fetchCategoriesRecursively())
            setSizeFull()
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
        }

        val refreshHandler: () -> Unit = { vaadinScope.launch { treeDataProvider.refreshAll() } }
        addAttachListener { service.refreshListeners += refreshHandler }
        addDetachListener { service.refreshListeners -= refreshHandler }
    }

    private fun refresh() {
        vaadinScope.launchWithReporting {
            report("ready2order-Produktkatalog aktualisieren...")
            application { service.refresh() }
            treeDataProvider.refreshAll()
        }
    }

    private fun editItem(item: Item) {
        when (item) {
            is CategoryItem -> editCategoryItem(item)
            is ProductItem -> editProductItem(item)
            is VariationItem -> editVariationItem(item)
        }
    }

    private fun editCategoryItem(category: CategoryItem) {
        vaadinScope.launch {
            val result = editCategoryDialogFactory(category.value, category.syncCategory)
            if (result != null) {
                application { service.update(result.sync) }
                treeDataProvider.refreshItem(category)
            }
        }
    }

    private fun editProductItem(product: ProductItem) {
        vaadinScope.launch {
            val result = editProductDialogFactory(product.value, product.syncProduct, vaadinScope)
            if (result != null) {
                application { service.update(result.artoo, result.sync) }
                treeDataProvider.refreshItem(product, true)
            }
        }
    }

    private fun editVariationItem(variation: VariationItem) {
        vaadinScope.launch {
            val result = editVariation(variation.value, variation.syncVariant)
            if (result != null) {
                application { service.update(result.artoo, result.sync) }
                treeDataProvider.refreshItem(variation)
            }
        }
    }

    private fun markForSync(product: ProductItem) {
        service.markForSync(product.syncProduct)
        treeDataProvider.refreshItem(product)
    }

    private fun generateNewBarcodes(product: ProductItem) {
        vaadinScope.launchWithReporting {
            application { service.generateNewBarcodes(product, ::report) }
        }
    }

    private fun createLabelsForVariations(product: ProductItem) {
        product.value.variations.forEach { labelService.createLabel(Article(product.value, it), 1) }
    }

    private fun treeItemLabel(item: Item) =
        root {
            horizontalLayout {
                alignItems = FlexComponent.Alignment.CENTER
                style["gap"] = "var(--lumo-space-xs)"

                val icon = when (item) {
                    is CategoryItem -> CustomIcon.CATEGORY
                    is ProductItem -> CustomIcon.PRODUCT
                    is VariationItem -> CustomIcon.VARIATION
                }
                span { add(icon.create().apply { setSize("var(--lumo-icon-size-s)"); color = "var(--lumo-secondary-text-color)" }) }
                add(item.name)
            }
        }

    private fun treeItemStatus(item: Item) =
        when (item) {
            is CategoryItem -> Icon()
            is ProductItem -> productItemStatus(item)
            is VariationItem -> variationItemStatus(item)
        }

    private fun productItemStatus(item: ProductItem): Icon {
        val problems = item.checkForProblems()
        val icon = when {
            problems.hasErrors() -> treeItemIcon(VaadinIcon.WARNING, "var(--lumo-error-color)")
            problems.isNotEmpty() -> treeItemIcon(VaadinIcon.WARNING, "var(--lumo-warning-color)")
            item.isMarkedForSync -> treeItemIcon(VaadinIcon.CHECK, "var(--lumo-success-color)")
            else -> treeItemIcon(VaadinIcon.CIRCLE, "var(--lumo-contrast-10pct)")
        }
        if (problems.isNotEmpty()) icon.setTooltipText(problems.toPresentationString())
        return icon
    }

    private fun variationItemStatus(item: VariationItem): Icon {
        val problems = item.checkForProblems()
        val icon = when {
            problems.hasErrors() -> treeItemIcon(VaadinIcon.WARNING, "var(--lumo-error-color)")
            problems.isNotEmpty() -> treeItemIcon(VaadinIcon.WARNING, "var(--lumo-warning-color)")
            else -> Icon()
        }
        if (problems.isNotEmpty()) icon.setTooltipText(problems.toPresentationString())
        return icon
    }

    private fun treeItemIcon(icon: VaadinIcon, color: String) =
        icon.create().apply { setSize("var(--lumo-icon-size-s)"); style.setColor(color) }

    private fun treeItemActions(item: Item) =
        root {
            horizontalLayout {
                isSpacing = false
                isPadding = false
                button(VaadinIcon.EDIT) {
                    addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON)
                    addClickListener { editItem(item) }
                }
                button(VaadinIcon.ELLIPSIS_DOTS_H) {
                    isEnabled = item is ProductItem
                    addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON)

                    ContextMenu(this).apply {
                        isOpenOnClick = true
                        if (item is ProductItem) {
                            addItem("Synchronisieren") { markForSync(item) }.apply { isEnabled = !item.isMarkedForSync }
                            addComponent(Hr())
                            addItem("Barcodes neu generieren") { generateNewBarcodes(item) }
                            addComponent(Hr())
                            addItem("Etikett für Produkt") {}
                            addItem("Etiketten für Varianten") { createLabelsForVariations(item) }
                        }
                    }
                }
            }
        }

    private inner class TreeDataProvider : AbstractBackEndHierarchicalDataProvider<Item, Unit>() {

        override fun fetchChildrenFromBackEnd(query: HierarchicalQuery<Item, Unit>): Stream<Item> {
            val markedForSync = markedForSyncFilterChipBox.value.takeIf { it.size == 1 }?.first()
            val hasProblems = hasProblemsFilterChipBox.value.takeIf { it.size == 1 }?.first()
            val children = query.parent?.children ?: service.rootCategories
            return children.asSequence()
                .filter { it.filterBy(markedForSync, hasProblems, filterTextField.value) }
                .drop(query.offset)
                .take(query.limit)
                .asStream()
        }

        override fun hasChildren(item: Item) = item.hasChildren
        override fun getChildCount(query: HierarchicalQuery<Item, Unit>) = fetchChildren(query).count().toInt()
        override fun getId(item: Item) = item.itemId

        fun fetchCategoriesRecursively() =
            fetchItemsRecursively(null)
                .filter { it is CategoryItem }
                .toList()

        private fun fetchItemsRecursively(parent: Item?): Sequence<Item> =
            fetchChildren(HierarchicalQuery(null, parent))
                .asSequence()
                .flatMap { sequence { yield(it); yieldAll(fetchItemsRecursively(it)) } }
    }
}
