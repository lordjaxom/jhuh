package de.hinundhergestellt.jhuh.sync

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Key
import com.vaadin.flow.component.Text
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.grid.ColumnTextAlign
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.Span
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
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asStream

private val logger = KotlinLogging.logger { }

@Route
@PageTitle("Produktverwaltung")
class ShopifySyncView(
    private val importService: ShopifyImportService,
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
        addAttachListener { importService.stateChangeListeners += stateChangedHandler }
        addDetachListener { importService.stateChangeListeners -= stateChangedHandler }
    }

    private fun configureHeader() {
        refreshButton.text = "Aktualisieren"
        refreshButton.isDisableOnClick = true
        refreshButton.addClickListener { importService.refreshItems() }

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
        treeGrid.addHierarchyColumn { it.name }
            .setHeader("Bezeichnung")
            .apply {
                isSortable = false
                flexGrow = 100
            }
        treeGrid.addColumn { it.vendor }
            .setHeader("Hersteller")
            .apply {
                isSortable = false
                flexGrow = 5
            }
        treeGrid.addColumn { it.type }
            .setHeader("Produktart")
            .apply {
                isSortable = false
                flexGrow = 5
            }
        treeGrid.addColumn { it.tags }
            .setHeader("Weitere Tags")
            .apply {
                isSortable = false
                flexGrow = 30
            }
        treeGrid.addColumn { it.variations }
            .setHeader("V#")
            .apply {
                isSortable = false
                textAlign = ColumnTextAlign.CENTER
                width = "4em"
                flexGrow = 0
            }
        treeGrid.addComponentColumn { treeItemStatusColumn(it) }
            .setHeader("")
            .apply {
                isSortable = false
                width = "32px"
                flexGrow = 0
            }
        treeGrid.addComponentColumn { treeItemActionsColumn(it) }
            .setHeader("")
            .apply {
                isSortable = false
                width = "72px"
                flexGrow = 0
            }
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
            .runAsync({ importService.synchronize() }, taskExecutor)
            .whenComplete { _, throwable ->
                ui.getOrNull()?.access {
                    throwable?.also { showErrorNotification(it) }
                    progressOverlay.isVisible = false
                    treeGrid.dataProvider.refreshAll()
                }
            }
    }

    private fun editItem(item: SyncableItem) {
        val dialog = Dialog()
        dialog.width = "400px"
        dialog.headerTitle = if (item is ShopifyImportService.Category) "Kategorie bearbeiten" else "Produkt bearbeiten"

        val closeButton = Button(VaadinIcon.CLOSE.create()) { dialog.close() }
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)
        dialog.header.add(closeButton)

        val layout = VerticalLayout()
        layout.isSpacing = false
        layout.isPadding = false
        dialog.add(layout)

        fun textFieldWithCheckboxForCategory(label: String, value: String): Pair<TextField, Checkbox?> {
            val textField = TextField()
            textField.label = label
            textField.value = value
            textField.isEnabled = item is ShopifyImportService.Product
            textField.setWidthFull()
            layout.add(textField)

            if (item is ShopifyImportService.Product) {
                return Pair(textField, null)
            }

            val checkbox = Checkbox("Für alle Produkte ersetzen?")
            checkbox.value = false
            checkbox.addValueChangeListener {
                textField.isEnabled = it.value
                if (it.value) textField.focus()
            }
            layout.add(checkbox)
            return Pair(textField, checkbox)
        }

        val (vendorTextField, vendorCheckbox) = textFieldWithCheckboxForCategory("Hersteller", item.vendor ?: "")
        val (typeTextField, typeCheckbox) = textFieldWithCheckboxForCategory("Produktart", item.type ?: "")

        val tagsTextField = TextField()
        tagsTextField.label = "Tags"
        tagsTextField.value = item.tags
        tagsTextField.setWidthFull()
        layout.add(tagsTextField)

        sequenceOf(vendorTextField, tagsTextField).first { it.isEnabled }.focus()

        val saveButton = Button("Speichern")
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY)
        saveButton.addClickShortcut(Key.ENTER)
        saveButton.addClickListener {
            dialog.isEnabled = false
            val vendor = vendorTextField.value.takeIf { vendorCheckbox?.value ?: true }
            val type = typeTextField.value.takeIf { typeCheckbox?.value ?: true }
            importService.updateItem(item, vendor, type, tagsTextField.value)
            dialog.close()
            refreshTreeItem(item, vendor != null || type != null)
        }
        dialog.footer.add(saveButton)
        dialog.open()
    }

    private fun treeItemStatusColumn(item: SyncableItem): Component {
        if (item !is ShopifyImportService.Product) {
            return Span()
        }

        val problems = item.syncProblems
        val icon = when {
            problems.has<SyncProblem.Error>() -> VaadinIcon.WARNING.create().apply { style.setColor("var(--lumo-error-color") }
            problems.isNotEmpty() -> VaadinIcon.WARNING.create().apply { style.setColor("var(--lumo-warning-color") }
            item.isMarkedForSync -> VaadinIcon.CHECK.create().apply { style.setColor("var(--lumo-success-color") }
            else -> VaadinIcon.CIRCLE.create().apply { style.setColor("lightgrey") }
        }
        icon.setSize("16px")
        if (problems.isNotEmpty()) {
            Tooltip.forComponent(icon).withText(problems.joinToString("\n"))
        }
        return icon
    }

    private fun treeItemActionsColumn(item: SyncableItem): Component {
        val spacer = Span()
        spacer.setWidthFull()

        val layout = HorizontalLayout(spacer)
        layout.isSpacing = false
        layout.themeList.add("spacing-s")
        layout.style.setWhiteSpace(Style.WhiteSpace.NOWRAP)

        if (item is ShopifyImportService.Product) {
            val marked = item.isMarkedForSync
            val problems = item.syncProblems

            val markUnmarkIcon = if (marked) VaadinIcon.TRASH.create() else VaadinIcon.PLUS.create()
            markUnmarkIcon.setSize("20px")
            val markUnmarkButton = Button(markUnmarkIcon)
            markUnmarkButton.isEnabled = marked || !problems.has<SyncProblem.Error>()
            markUnmarkButton.height = "20px"
            markUnmarkButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
            markUnmarkButton.addClickListener {
                if (marked) {
                    importService.unmarkForSync(item)
                } else {
                    importService.markForSync(item)
                }
                treeGrid.dataProvider.refreshItem(item)
            }
            layout.add(markUnmarkButton)
        }

        val editIcon = VaadinIcon.EDIT.create()
        editIcon.setSize("20px")
        val editButton = Button(editIcon) { editItem(item) }
        editButton.height = "20px"
        editButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
        layout.add(editButton)

        return layout
    }

    private fun refreshTreeItem(item: SyncableItem, recurse: Boolean) {
        treeGrid.dataProvider.refreshItem(item, recurse)
        if (recurse && item is ShopifyImportService.Category) {
            item.childrenAndProducts.forEach { refreshTreeItem(it, true) }
        }
    }

    private inner class TreeDataProvider : AbstractBackEndHierarchicalDataProvider<SyncableItem, Void?>() {

        override fun fetchChildrenFromBackEnd(query: HierarchicalQuery<SyncableItem, Void?>): Stream<SyncableItem> {
            val withErrors = if (withErrorsCheckbox.value) true else if (errorFreeCheckbox.value) false else null
            val children = (query.parent as ShopifyImportService.Category?)?.childrenAndProducts ?: importService.rootCategories
            return children.asSequence()
                .filter { it.filterBy(markedForSyncCheckbox.value, withErrors, filterTextField.value) }
                .sortedBy { it.name }
                .asStream()
        }

        override fun hasChildren(item: SyncableItem) =
            item is ShopifyImportService.Category

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
