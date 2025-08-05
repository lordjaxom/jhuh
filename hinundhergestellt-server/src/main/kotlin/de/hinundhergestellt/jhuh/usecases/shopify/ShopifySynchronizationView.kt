package de.hinundhergestellt.jhuh.usecases.shopify

import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.shared.Tooltip
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery
import com.vaadin.flow.dom.Style
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.GridActionButton
import de.hinundhergestellt.jhuh.components.ProgressOverlay
import de.hinundhergestellt.jhuh.components.actionsColumn
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.checkbox
import de.hinundhergestellt.jhuh.components.countColumn
import de.hinundhergestellt.jhuh.components.hierarchyTextColumn
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.iconColumn
import de.hinundhergestellt.jhuh.components.progressOverlay
import de.hinundhergestellt.jhuh.components.showErrorNotification
import de.hinundhergestellt.jhuh.components.textColumn
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.treeGrid
import de.hinundhergestellt.jhuh.components.vaadinScope
import de.hinundhergestellt.jhuh.usecases.labels.LabelGeneratorService
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.CategoryItem
import de.hinundhergestellt.jhuh.usecases.shopify.ShopifySynchronizationService.ProductItem
import de.hinundhergestellt.jhuh.usecases.shopify.SyncProblem.Error
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.stream.Stream
import kotlin.streams.asStream

@Route
@PageTitle("Shopify-Synchronisation")
class ShopifySynchronizationView(
    private val service: ShopifySynchronizationService,
    private val labelService: LabelGeneratorService,
    private val applicationScope: CoroutineScope
) : VerticalLayout() {

    private val markedForSyncCheckbox: Checkbox
    private val withErrorsCheckbox: Checkbox
    private val errorFreeCheckbox: Checkbox
    private val filterTextField: TextField
    private val progressOverlay: ProgressOverlay

    private val treeDataProvider = TreeDataProvider()

    private val vaadinScope = vaadinScope(this)

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
            button("Mit Shopify synchronisieren") {
                addClickListener { synchronize() }
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
            withErrorsCheckbox = checkbox("Nur fehlerhaft") {
                value = false
                style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
            }
            errorFreeCheckbox = checkbox("Nur fehlerfrei") {
                value = false
                style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
            }
            filterTextField = textField() {
                placeholder = "Suche..."
                prefixComponent = VaadinIcon.SEARCH.create()
                isClearButtonVisible = true
                setWidthFull()
                addValueChangeListener { treeDataProvider.refreshAll() }
            }

            withErrorsCheckbox.addValueChangeListener { if (it.value) errorFreeCheckbox.value = false; treeDataProvider.refreshAll() }
            errorFreeCheckbox.addValueChangeListener { if (it.value) withErrorsCheckbox.value = false; treeDataProvider.refreshAll() }
        }
        treeGrid<SyncableItem> {
            hierarchyTextColumn("Bezeichnung", flexGrow = 100) { it.name }.setTooltipGenerator { it.name }
            textColumn("Hersteller", flexGrow = 5) { it.vendor?.name }
            textColumn("Produktart", flexGrow = 5) { it.type }
            textColumn("Weitere Tags", flexGrow = 30) { it.tags }
            countColumn("V#") { it.variations }
            iconColumn { syncableItemStatus(it) }
            actionsColumn(3) { syncableItemActions(it) }
            setDataProvider(treeDataProvider)
            expandRecursively(treeDataProvider.fetchChildren(HierarchicalQuery(null, null)), Int.MAX_VALUE)
            setSizeFull()
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
        }
        progressOverlay = progressOverlay()

        val refreshHandler: () -> Unit = { vaadinScope.launch { treeDataProvider.refreshAll(); progressOverlay.isVisible = false } }
        addAttachListener { service.refreshListeners += refreshHandler }
        addDetachListener { service.refreshListeners -= refreshHandler }
    }

    private fun refresh() {
        progressOverlay.isVisible = true
        service.refresh()
    }

    private fun synchronize() = vaadinScope.launch {
        progressOverlay.text = "Synchronisiere Produkte mit Shopify..."
        progressOverlay.isVisible = true
        try {
            withContext(applicationScope.coroutineContext) {
                service.synchronize {
                    vaadinScope.launch { progressOverlay.text = it }
                }
            }
        } catch (e: Throwable) {
            showErrorNotification(e)
        } finally {
            progressOverlay.isVisible = false
            treeDataProvider.refreshAll()
        }
    }

    private fun markItem(product: ProductItem) {
        service.markForSync(product)
        treeDataProvider.refreshItem(product)
    }

    private fun unmarkItem(product: ProductItem) {
        service.unmarkForSync(product)
        treeDataProvider.refreshItem(product)
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
        }

    private inner class TreeDataProvider : AbstractBackEndHierarchicalDataProvider<SyncableItem, Void?>() {

        override fun fetchChildrenFromBackEnd(query: HierarchicalQuery<SyncableItem, Void?>): Stream<SyncableItem> {
            val withErrors = if (withErrorsCheckbox.value) true else if (errorFreeCheckbox.value) false else null
            val children = (query.parent as CategoryItem?)?.children ?: service.rootCategories
            return children.asSequence()
                .filter { it.filterBy(markedForSyncCheckbox.value, withErrors, filterTextField.value) }
                .drop(query.offset)
                .take(query.limit)
                .asStream()
        }

        override fun hasChildren(item: SyncableItem) = item is CategoryItem
        override fun getChildCount(query: HierarchicalQuery<SyncableItem, Void?>) = fetchChildren(query).count().toInt()
        override fun getId(item: SyncableItem) = item.itemId
    }
}
