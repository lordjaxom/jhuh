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
import com.vaadin.flow.component.menubar.MenuBar
import com.vaadin.flow.component.menubar.MenuBarVariant
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
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedCategory
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedProduct
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asStream

@Route
@PageTitle("Datenabgleich mit Shopify")
class ArtooImportView(
    private val importService: ArtooImportService,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor
) : VerticalLayout() {

    private lateinit var markedForSyncCheckbox: Checkbox
    private lateinit var withErrorsCheckbox: Checkbox
    private lateinit var errorFreeCheckbox: Checkbox
    private lateinit var filterTextField: TextField
    private lateinit var treeGrid: TreeGrid<Any>
    private lateinit var progressOverlay: Div

    init {
        setHeightFull()
        width = "1170px"
        style.setMargin("0 auto")

        createHeader()
        createFilters()
        createTreeGrid()
        createProgressOverlay()
    }

    private fun createHeader() {
        val menuBar = MenuBar()
        menuBar.setWidthFull()
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED)
        menuBar.addItem("Synchronisieren") { synchronize() }
        add(menuBar)
    }

    private fun createFilters() {
        markedForSyncCheckbox = Checkbox("Nur synchronisiert")
        withErrorsCheckbox = Checkbox("Nur fehlerhaft")
        errorFreeCheckbox = Checkbox("Nur fehlerfrei")

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

        filterTextField = TextField()
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

    private fun createTreeGrid() {
        treeGrid = TreeGrid<Any>()
        treeGrid.addHierarchyColumn { importService.getItemName(it) }
            .setHeader("Bezeichnung")
            .apply {
                isSortable = false
                flexGrow = 100
            }
        treeGrid.addColumn { importService.getItemVendor(it) }
            .setHeader("Hersteller")
            .apply {
                isSortable = false
                flexGrow = 5
            }
        treeGrid.addColumn { importService.getItemType(it) }
            .setHeader("Produktart")
            .apply {
                isSortable = false
                flexGrow = 5
            }
        treeGrid.addColumn { importService.getItemTags(it) }
            .setHeader("Weitere Tags")
            .apply {
                isSortable = false
                flexGrow = 30
            }
        treeGrid.addColumn { importService.getItemVariations(it) }
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

    private fun createProgressOverlay() {
        val progressSpinner = Div()
        progressSpinner.className = "progress-spinner"
        progressOverlay = Div(progressSpinner)
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

    private fun editItem(item: Any) {
        val dialog = Dialog()
        dialog.width = "400px"
        dialog.headerTitle = if (item is ArtooMappedCategory) "Kategorie bearbeiten" else "Produkt bearbeiten"

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
            textField.isEnabled = item is ArtooMappedProduct
            textField.setWidthFull()
            layout.add(textField)

            if (item is ArtooMappedProduct) {
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

        val (vendorTextField, vendorCheckbox) = textFieldWithCheckboxForCategory("Hersteller", importService.getItemVendor(item))
        val (typeTextField, typeCheckbox) = textFieldWithCheckboxForCategory("Produktart", importService.getItemType(item))

        val tagsTextField = TextField()
        tagsTextField.label = "Tags"
        tagsTextField.value = importService.getItemTags(item)
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
            treeGrid.dataProvider.refreshItem(item, vendor != null || type != null)
        }
        dialog.footer.add(saveButton)
        dialog.open()
    }

    private fun treeItemStatusColumn(item: Any): Component {
        if (item !is ArtooMappedProduct) {
            return Span()
        }

        val problems = importService.getSyncProblems(item)
        val icon = when {
            problems.has<SyncProblem.Error>() -> VaadinIcon.WARNING.create().apply { style.setColor("var(--lumo-error-color") }
            problems.isNotEmpty() -> VaadinIcon.WARNING.create().apply { style.setColor("var(--lumo-warning-color") }
            importService.isMarkedForSync(item) -> VaadinIcon.CHECK.create().apply { style.setColor("var(--lumo-success-color") }
            else -> VaadinIcon.CIRCLE.create().apply { style.setColor("var(--lumo-tertiary-color") }
        }
        icon.setSize("16px")
        if (problems.isNotEmpty()) {
            Tooltip.forComponent(icon).withText(problems.joinToString("\n"))
        }
        return icon
    }

    private fun treeItemActionsColumn(item: Any): Component {
        val spacer = Span()
        spacer.setWidthFull()

        val layout = HorizontalLayout(spacer)
        layout.isSpacing = false
        layout.themeList.add("spacing-s")
        layout.style.setWhiteSpace(Style.WhiteSpace.NOWRAP)

        if (item is ArtooMappedProduct) {
            val marked = importService.isMarkedForSync(item)
            val problems = importService.getSyncProblems(item)

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

    private inner class TreeDataProvider : AbstractBackEndHierarchicalDataProvider<Any, Void?>() {

        override fun fetchChildrenFromBackEnd(query: HierarchicalQuery<Any, Void?>): Stream<Any> {
            val withErrors = if (withErrorsCheckbox.value) true else if (errorFreeCheckbox.value) false else null
            val children = (query.parent as ArtooMappedCategory?)
                ?.let { it.children.asSequence() + it.products.asSequence() }
                ?: importService.rootCategories.asSequence()
            return children
                .filter { importService.filterBy(it, markedForSyncCheckbox.value, withErrors, filterTextField.value) }
                .sortedBy { importService.getItemName(it) }
                .asStream()
        }

        override fun hasChildren(item: Any) =
            item is ArtooMappedCategory

        override fun getChildCount(query: HierarchicalQuery<Any, Void?>) =
            fetchChildren(query).count().toInt()
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
