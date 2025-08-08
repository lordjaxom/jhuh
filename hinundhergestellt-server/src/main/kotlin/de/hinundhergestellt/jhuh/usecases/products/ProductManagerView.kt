package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery
import com.vaadin.flow.dom.Style
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.Article
import de.hinundhergestellt.jhuh.components.CustomIcon
import de.hinundhergestellt.jhuh.components.GridActionButton
import de.hinundhergestellt.jhuh.components.MoreGridActionButton
import de.hinundhergestellt.jhuh.components.VaadinCoroutineScope
import de.hinundhergestellt.jhuh.components.actionsColumn
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.checkbox
import de.hinundhergestellt.jhuh.components.hierarchyComponentColumn
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.progressOverlay
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.treeGrid
import de.hinundhergestellt.jhuh.usecases.labels.LabelGeneratorService
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.CategoryTreeItem
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.ProductTreeItem
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.VariationTreeItem
import kotlinx.coroutines.CoroutineScope
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream

@Route
@PageTitle("Produktverwaltung")
class ProductManagerView(
    private val service: ProductManagerService,
    private val labelService: LabelGeneratorService,
    applicationScope: CoroutineScope
) : VerticalLayout() {

    private val markedForSyncCheckbox: Checkbox
    private val filterTextField: TextField
    private val itemsGrid: TreeGrid<TreeItem>
    private val progressOverlay = progressOverlay()

    private val treeDataProvider = TreeDataProvider()

    private val vaadinScope = VaadinCoroutineScope(this, applicationScope, progressOverlay)

    init {
        setHeightFull()
        width = "1170px"
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

            markedForSyncCheckbox = checkbox("Nur synchronisiert") {
                value = true
                style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
                addValueChangeListener { treeDataProvider.refreshAll() }
            }
            filterTextField = textField {
                placeholder = "Suche..."
                prefixComponent = VaadinIcon.SEARCH.create()
                isClearButtonVisible = true
                setWidthFull()
                addValueChangeListener { treeDataProvider.refreshAll() }
            }
        }
        itemsGrid = treeGrid<TreeItem> {
            hierarchyComponentColumn("Bezeichnung", flexGrow = 100) { treeItemLabel(it) }
            actionsColumn(3) { treeItemActions(it) }
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

    private fun editItem(item: TreeItem) {
        when (item) {
            is ProductTreeItem -> editProductItem(item)
            is VariationTreeItem -> editVariationItem(item)
            else -> Unit
        }
//        vaadinScope.launch {
//            val result = editItemDialog(item, service.vendors)
//            if (result == null) return@launch
//
//            service.updateItem(item, result.vendor, result.replaceVendor, result.type.ifEmpty { null }, result.replaceType, result.tags)
//            treeDataProvider.refreshItem(item, result.replaceVendor || result.replaceType)
//        }
    }

    private fun editProductItem(product: ProductTreeItem) {
        vaadinScope.launch {
            if (editProduct(product.value)) {
                application { service.update(product.value) }
                treeDataProvider.refreshItem(product)
            }
        }
    }

    private fun editVariationItem(variation: VariationTreeItem) {
        vaadinScope.launch {
            if (editVariation(variation.value)) {
                application { service.update(variation.value) }
                treeDataProvider.refreshItem(variation)
            }
        }
    }

    private fun generateNewBarcodes(product: ProductTreeItem) {
        vaadinScope.launchWithReporting {
            application { service.generateNewBarcodes(product, ::report) }
        }
    }

    private fun createLabelsForVariations(product: ProductTreeItem) {
        product.value.variations.forEach { labelService.createLabel(Article(product.value, it), 1) }
    }

    private fun treeItemLabel(item: TreeItem) =
        HorizontalLayout().apply {
            alignItems = FlexComponent.Alignment.CENTER
            style.set("gap", "var(--lumo-space-xs)")

            val icon = when (item) {
                is CategoryTreeItem -> CustomIcon.CATEGORY
                is ProductTreeItem -> CustomIcon.PRODUCT
                is VariationTreeItem -> CustomIcon.VARIATION
            }
            add(icon.create().apply { color = "var(--lumo-secondary-text-color)" })
            add(item.name)
        }

    private fun treeItemActions(item: TreeItem) =
        buildList {
            add(GridActionButton(VaadinIcon.EDIT) { editItem(item) })
            add(MoreGridActionButton().apply {
                if (item is ProductTreeItem) {
                    addItem("Barcodes neu generieren") { generateNewBarcodes(item) }
                    addDivider()
                    addItem("Etikett für Produkt") {}
                    addItem("Etiketten für Varianten") { createLabelsForVariations(item) }
                }
                isEnabled = item is ProductTreeItem
            })
        }

    private inner class TreeDataProvider : AbstractBackEndHierarchicalDataProvider<TreeItem, Void?>() {

        override fun fetchChildrenFromBackEnd(query: HierarchicalQuery<TreeItem, Void?>): Stream<TreeItem> {
            val children = query.parent?.children ?: service.rootCategories
            return children.asSequence()
                .filter { it.filterBy(markedForSyncCheckbox.value, filterTextField.value) }
                .drop(query.offset)
                .take(query.limit)
                .asStream()
        }

        override fun hasChildren(item: TreeItem) = item.hasChildren
        override fun getChildCount(query: HierarchicalQuery<TreeItem, Void?>) = fetchChildren(query).count().toInt()
        override fun getId(item: TreeItem) = item.itemId

        fun fetchCategoriesRecursively() =
            fetchItemsRecursively(null)
                .filter { it is CategoryTreeItem }
                .toList()

        private fun fetchItemsRecursively(parent: TreeItem?): Sequence<TreeItem> =
            fetchChildren(HierarchicalQuery(null, parent))
                .asSequence()
                .flatMap { sequence { yield(it); yieldAll(fetchItemsRecursively(it)) } }
    }
}
