package de.hinundhergestellt.jhuh.sync

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.HasText
import com.vaadin.flow.component.Text
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.grid.ColumnTextAlign
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery
import com.vaadin.flow.data.renderer.ComponentRenderer
import com.vaadin.flow.dom.Style
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
class ArtooImportView(
    private val importService: ArtooImportService,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: AsyncTaskExecutor
) : VerticalLayout() {

    private lateinit var treeGrid: TreeGrid<Any>
    private lateinit var progressOverlay: Div

    private var onlyMarkedForSync = true
    private var onlyReadyForSync = false
    private var onlyMarkedWithErrors = false

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
        val title = H3("Artikel aus ready2order importieren")
        title.whiteSpace = HasText.WhiteSpace.NOWRAP
        title.style.setMargin("auto 0")

        val spacer = Div()
        spacer.setWidthFull()

        val button = Button("Synchronisieren")
        button.style
            .setPosition(Style.Position.RELATIVE)
            .setRight("5px")
        button.addClickListener { synchronize() }

        val titleLayout = HorizontalLayout(title, spacer, button)
        titleLayout.isPadding = true
        titleLayout.setWidthFull()
        titleLayout.style.setBackgroundColor("var(--lumo-primary-color-10pct)")
        add(titleLayout)
    }

    private fun createFilters() {
        val spacer = Div()
        spacer.setWidthFull()

        val markedCheckbox = Checkbox("Nur synchronisiert")
        val readyCheckbox = Checkbox("Bereit zum Synchronisieren")
        val errorsCheckbox = Checkbox("Synchronisiert mit Fehlern")

        markedCheckbox.value = onlyMarkedForSync
        markedCheckbox.style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
        markedCheckbox.addValueChangeListener {
            onlyMarkedForSync = it.value
            if (it.value) {
                readyCheckbox.value = false
                errorsCheckbox.value = false
            }
            treeGrid.dataProvider.refreshAll()
        }

        readyCheckbox.value = onlyReadyForSync
        readyCheckbox.style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
        readyCheckbox.addValueChangeListener {
            onlyReadyForSync = it.value
            if (it.value) {
                markedCheckbox.value = false
                errorsCheckbox.value = false
            }
            treeGrid.dataProvider.refreshAll()
        }

        errorsCheckbox.value = onlyMarkedWithErrors
        errorsCheckbox.style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
        errorsCheckbox.addValueChangeListener { event ->
            onlyMarkedWithErrors = event.value
            if (event.value) {
                markedCheckbox.value = false
                readyCheckbox.value = false
            }
            treeGrid.dataProvider.refreshAll()
        }

        val filtersLayout = HorizontalLayout(spacer, markedCheckbox, readyCheckbox, errorsCheckbox)
        filtersLayout.setWidthFull()
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
        treeGrid.addColumn(ComponentRenderer { it -> treeItemTagsColumn(it) })
            .setHeader("Tags")
            .apply {
                isSortable = false
                flexGrow = 50
            }
        treeGrid.addColumn { importService.getItemVariations(it) }
            .setHeader("V#")
            .apply {
                isSortable = false
                textAlign = ColumnTextAlign.CENTER
                width = "4em"
                flexGrow = 0
            }
        treeGrid.addColumn(ComponentRenderer { it -> treeItemStatusColumn(it) })
            .setHeader("").apply {
                isSortable = false
                width = "32px"
                flexGrow = 0
            }
        treeGrid.addColumn(ComponentRenderer { it -> treeItemActionsColumn(it) })
            .setHeader("").apply {
                isSortable = false
                width = "56px"
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

    private fun editItemTags(item: Any) {
        val dialog = Dialog()
        dialog.headerTitle = "Tags bearbeiten"
        val closeButton = Button(VaadinIcon.CLOSE.create()) { dialog.close() }
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY)
        dialog.header.add(closeButton)
        val textField = TextField()
        textField.value = importService.getItemTags(item)
        dialog.add(textField)
        dialog.footer.add(Button("Speichern") {
            importService.updateItemTags(item, textField.value)
            treeGrid.dataProvider.refreshItem(item)
            dialog.close()
        })
        dialog.open()
    }

    private fun treeItemTagsColumn(item: Any): Component {
        val icon = VaadinIcon.EDIT.create()
        icon.setSize("20px")
        val button = Button(icon) { editItemTags(item) }
        button.height = "20px"
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
        val text = Text(importService.getItemTags(item))
        val layout = HorizontalLayout(button, text)
        layout.isSpacing = false
        layout.themeList.add("spacing-s")
        layout.style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
        return layout
    }

    private fun treeItemStatusColumn(item: Any): Component {
        if (item !is ArtooMappedProduct) {
            return Span()
        }

        val ready = item.isReadyForSync
        val marked = importService.isMarkedForSync(item)
        val icon = when {
            !ready -> VaadinIcon.WARNING.create().apply {
                style.setColor("var(--lumo-warning-color)")
                //Tooltip.forComponent(icon).withText("Fehler beim Laden der Artikel");
            }

            !marked -> VaadinIcon.CIRCLE.create().apply {
                style.setColor("lightgray")
            }

            else -> VaadinIcon.CHECK.create().apply {
                style.setColor("var(--lumo-success-color)")
            }
        }
        icon.setSize("16px")
        return icon
    }

    private fun treeItemActionsColumn(item: Any): Component {
        if (item !is ArtooMappedProduct) {
            return Span()
        }

        val ready = item.isReadyForSync
        val marked = importService.isMarkedForSync(item)
        val icon = if (marked) VaadinIcon.TRASH.create() else VaadinIcon.PLUS.create()
        icon.setSize("20px")
        //icon.color = if (!ready) "lightgray" else if (marked) "red" else "green"
        val button = Button(icon)
        button.isEnabled = ready
        button.height = "20px"
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
        button.addClickListener {
            if (marked) {
                importService.unmarkForSync(item)
            } else {
                importService.markForSync(item)
            }
            treeGrid.dataProvider.refreshItem(item)
        }
        return button
    }

    private inner class TreeDataProvider : AbstractBackEndHierarchicalDataProvider<Any, Void?>() {

        override fun fetchChildrenFromBackEnd(query: HierarchicalQuery<Any, Void?>): Stream<Any> {
            return (query.parent
                ?.let { (it as ArtooMappedCategory).children.asSequence() + it.products.asSequence() }
                ?: importService.rootCategories.asSequence())
                .filter { !onlyMarkedForSync || importService.filterByMarkedForSync(it) }
                .filter { !onlyReadyForSync || importService.filterByReadyToSync(it) }
                .filter { !onlyMarkedWithErrors || importService.filterByMarkedWithErrors(it) }
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
