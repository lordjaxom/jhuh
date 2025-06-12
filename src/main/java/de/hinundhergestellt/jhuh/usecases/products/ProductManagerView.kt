package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.Text
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.shared.Tooltip
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery
import com.vaadin.flow.dom.Style
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.GridActionButton
import de.hinundhergestellt.jhuh.components.MoreGridActionButton
import de.hinundhergestellt.jhuh.components.addActionsColumn
import de.hinundhergestellt.jhuh.components.addCountColumn
import de.hinundhergestellt.jhuh.components.addHierarchyTextColumn
import de.hinundhergestellt.jhuh.components.addIconColumn
import de.hinundhergestellt.jhuh.components.addTextColumn
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.CategoryItem
import de.hinundhergestellt.jhuh.usecases.products.ProductManagerService.ProductItem
import de.hinundhergestellt.jhuh.usecases.products.SyncProblem.Error
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asStream

@Route
@PageTitle("Produktverwaltung")
class ProductManagerView(
    private val service: ProductManagerService,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor
) : VerticalLayout() {

    private val refreshButton = Button()
    private val markedForSyncCheckbox = Checkbox()
    private val withErrorsCheckbox = Checkbox()
    private val errorFreeCheckbox = Checkbox()
    private val filterTextField = TextField()
    private val treeGrid = TreeGrid<SyncableItem>()
    private val progressOverlay = Div()

    init {
        setHeightFull()
        width = "1170px"
        style.setMargin("0 auto")

        configureHeader()
        configureFilters()
        configureTreeGrid()
        configureProgressOverlay()

        val stateChangedHandler: () -> Unit =
            { ui.getOrNull()?.access { treeGrid.dataProvider.refreshAll(); refreshButton.isEnabled = true } }
        addAttachListener { service.stateChangeListeners += stateChangedHandler }
        addDetachListener { service.stateChangeListeners -= stateChangedHandler }
    }

    private fun configureHeader() {
        refreshButton.text = "Aktualisieren"
        refreshButton.isDisableOnClick = true
        refreshButton.addClickListener { service.refreshItems() }

        val syncWithShopifyButton = Button("Mit Shopify synchronisieren") { synchronize() }

        val layout = HorizontalLayout(refreshButton, syncWithShopifyButton)
        layout.justifyContentMode = FlexComponent.JustifyContentMode.END
        layout.setWidthFull()
        add(layout)
    }

    private fun configureFilters() {
        markedForSyncCheckbox.label = "Nur synchronisiert"
        withErrorsCheckbox.label = "Nur fehlerhaft"
        errorFreeCheckbox.label = "Nur fehlerfrei"

        fun mutualExclusiveFilterCheckbox(checkbox: Checkbox, value: Boolean, vararg others: Checkbox) {
            checkbox.value = value
            checkbox.style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
            checkbox.addValueChangeListener {
                if (it.value) {
                    others.forEach { other -> other.value = false }
                }
                treeGrid.dataProvider.refreshAll()
            }
        }

        mutualExclusiveFilterCheckbox(markedForSyncCheckbox, true)
        mutualExclusiveFilterCheckbox(withErrorsCheckbox, false, errorFreeCheckbox)
        mutualExclusiveFilterCheckbox(errorFreeCheckbox, false, withErrorsCheckbox)

        filterTextField.placeholder = "Suche..."
        filterTextField.prefixComponent = VaadinIcon.SEARCH.create()
        filterTextField.isClearButtonVisible = true
        filterTextField.setWidthFull()
        filterTextField.addValueChangeListener { treeGrid.dataProvider.refreshAll() }

        val filtersLayout = HorizontalLayout(markedForSyncCheckbox, withErrorsCheckbox, errorFreeCheckbox, filterTextField)
        filtersLayout.setWidthFull()
        filtersLayout.alignItems = FlexComponent.Alignment.CENTER
        add(filtersLayout)
    }

    private fun configureTreeGrid() {
        treeGrid.addHierarchyTextColumn("Bezeichnung", flexGrow = 100) { it.name }
        treeGrid.addTextColumn("Hersteller", flexGrow = 5) { it.vendor?.name }
        treeGrid.addTextColumn("Produktart", flexGrow = 5) { it.type }
        treeGrid.addTextColumn("Weitere Tags", flexGrow = 30) { it.tags }
        treeGrid.addCountColumn("V#") { it.variations }
        treeGrid.addIconColumn { syncableItemStatus(it) }
        treeGrid.addActionsColumn(3) { syncableItemActions(it) }
        treeGrid.setDataProvider(TreeDataProvider())
        treeGrid.expandRecursively(
            treeGrid.dataProvider.fetchChildren(HierarchicalQuery(null, null)),
            Int.Companion.MAX_VALUE
        )
        treeGrid.setSizeFull()
        treeGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
        add(treeGrid)
    }

    private fun configureProgressOverlay() {
        val progressSpinner = Div()
        progressSpinner.className = "progress-spinner"
        progressOverlay.add(progressSpinner)
        progressOverlay.addClassName("progress-overlay")
        progressOverlay.isVisible = false
        add(progressOverlay)
    }

    private fun synchronize() {
        progressOverlay.isVisible = true
        CompletableFuture
            .runAsync({ service.synchronize() }, taskExecutor)
            .whenComplete { _, throwable ->
                ui.getOrNull()?.access {
                    throwable?.also { showErrorNotification(it) }
                    progressOverlay.isVisible = false
                    treeGrid.dataProvider.refreshAll()
                }
            }
    }

    private fun markItem(product: ProductItem) {
        service.markForSync(product)
        treeGrid.dataProvider.refreshItem(product)
    }

    private fun unmarkItem(product: ProductItem) {
        service.unmarkForSync(product)
        treeGrid.dataProvider.refreshItem(product)
    }

    private fun editItem(item: SyncableItem) {
        EditItemDialog(item, service.vendors) { vendor, type, tags ->
            service.updateItem(item, vendor, type, tags)
            refreshTreeItem(item, vendor != null || type != null)
        }
    }

    private fun syncableItemStatus(item: SyncableItem): Icon =
        if (item !is ProductItem) Icon()
        else {
            val problems = item.syncProblems
            when {
                problems.has<Error>() -> VaadinIcon.WARNING.create().apply { style.setColor("var(--lumo-error-color") }
                problems.isNotEmpty() -> VaadinIcon.WARNING.create().apply { style.setColor("var(--lumo-warning-color") }
                item.isMarkedForSync -> VaadinIcon.CHECK.create().apply { style.setColor("var(--lumo-success-color") }
                else -> VaadinIcon.CIRCLE.create().apply { style.setColor("lightgrey") }
            }.also {
                it.setSize("16px")
                if (problems.isNotEmpty()) {
                    Tooltip.forComponent(it).withText(problems.joinToString("\n"))
                }
            }
        }

    private fun syncableItemActions(item: SyncableItem) =
        buildList {
            if (item is ProductItem) {
                val markUnmarkButton =
                    if (item.isMarkedForSync) GridActionButton(VaadinIcon.TRASH) { unmarkItem(item) }
                    else GridActionButton(VaadinIcon.PLUS) { markItem(item) }.apply { isEnabled = !item.syncProblems.has<Error>() }
                add(markUnmarkButton)
            }
            add(GridActionButton(VaadinIcon.EDIT) { editItem(item) })
            add(MoreGridActionButton().apply {
                addItem("Etikett für Produkt") {}
                addItem("Etiketten für Varianten") {}
            })
        }

    private fun refreshTreeItem(item: SyncableItem, recurse: Boolean) {
        treeGrid.dataProvider.refreshItem(item, recurse)
        if (recurse && item is CategoryItem) {
            item.childrenAndProducts.forEach { refreshTreeItem(it, true) }
        }
    }

    private inner class TreeDataProvider : AbstractBackEndHierarchicalDataProvider<SyncableItem, Void?>() {

        override fun fetchChildrenFromBackEnd(query: HierarchicalQuery<SyncableItem, Void?>): Stream<SyncableItem> {
            val withErrors = if (withErrorsCheckbox.value) true else if (errorFreeCheckbox.value) false else null
            val children = (query.parent as CategoryItem?)?.childrenAndProducts ?: service.rootCategories
            return children.asSequence()
                .filter { it.filterBy(markedForSyncCheckbox.value, withErrors, filterTextField.value) }
                .sortedBy { it.name }
                .asStream()
        }

        override fun hasChildren(item: SyncableItem) =
            item is CategoryItem

        override fun getChildCount(query: HierarchicalQuery<SyncableItem, Void?>) =
            fetchChildren(query).count().toInt()

        override fun getId(item: SyncableItem) =
            item.itemId
    }
}

private fun showErrorNotification(error: Throwable) {
    val notification = Notification()
    notification.position = Notification.Position.TOP_CENTER
    notification.duration = Int.Companion.MAX_VALUE
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR)
    val button = Button(VaadinIcon.CLOSE_SMALL.create()) { _ -> notification.close() }
    button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
    val layout = HorizontalLayout(VaadinIcon.WARNING.create(), Text(error.message), button)
    layout.alignItems = FlexComponent.Alignment.CENTER
    notification.add(layout)
    notification.open()
}
