package de.hinundhergestellt.jhuh.sync

import com.vaadin.flow.component.*
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.html.Div
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.icon.Icon
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.treegrid.TreeGrid
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery
import com.vaadin.flow.data.renderer.ComponentRenderer
import com.vaadin.flow.dom.Style
import com.vaadin.flow.function.ValueProvider
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedCategory
import de.hinundhergestellt.jhuh.service.ready2order.ArtooMappedProduct
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.lang.Nullable
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
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
        //        setSpacing(false);
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

        val button = Button("Sync mit Shopify")
        button.style
            .setPosition(Style.Position.RELATIVE)
            .setRight("5px")
        button.addClickListener { syncWithSpotify() }

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
        treeGrid.addHierarchyColumn(ValueProvider { item: Any? -> importService.getItemName(item!!) })
            .setHeader("Bezeichnung")
            .setSortable(false).flexGrow = 10
        treeGrid.addColumn(ValueProvider { item: Any? -> importService.getItemVariations(item!!) })
            .setHeader("Variationen")
            .setSortable(false).flexGrow = 1
        treeGrid.addColumn(ComponentRenderer<Icon, Any> { it -> treeItemStatusIcon(it) })
            .setHeader("")
            .setSortable(false)
            .setWidth("32px").flexGrow = 0
        treeGrid.addColumn(ComponentRenderer<Button, Any> { it -> treeItemSyncButton(it) })
            .setHeader("")
            .setSortable(false)
            .setWidth("80px").flexGrow = 0
        treeGrid.setDataProvider(TreeDataProvider())
        treeGrid.expandRecursively(
            treeGrid.getDataProvider().fetchChildren(HierarchicalQuery(null, null)),
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

    private fun syncWithSpotify() {
        progressOverlay.isVisible = true
        CompletableFuture
            .runAsync({ importService.syncWithShopify() }, taskExecutor)
            .whenComplete { _, throwable -> handleSyncWithSpotifyCompleteAsync(throwable) }
    }

    private fun handleSyncWithSpotifyCompleteAsync(throwable: Throwable?) {
        ui.ifPresent {
            it.access {
                progressOverlay.isVisible = false
                treeGrid.dataProvider.refreshAll()
                if (throwable != null) {
                    showErrorNotification(throwable)
                }
            }
        }
    }

    @Nullable
    private fun treeItemStatusIcon(item: Any): Icon? {
        if (item !is ArtooMappedProduct) {
            return null
        }

        val ready = item.isReadyForSync
        val marked = importService.isMarkedForSync(item)
        val icon = when {
            !ready -> VaadinIcon.WARNING.create().apply {
                style.set("color", "var(--lumo-warning-color)")
                //Tooltip.forComponent(icon).withText("Fehler beim Laden der Artikel");
            }

            !marked -> VaadinIcon.CIRCLE.create().apply {
                style.set("color", "lightgray")
            }

            else -> VaadinIcon.CHECK.create().apply {
                style.set("color", "var(--lumo-success-color)")
            }
        }
        icon.setSize("16px")
        return icon
    }

    @Nullable
    private fun treeItemSyncButton(item: Any): Button? {
        if (item !is ArtooMappedProduct) {
            return null
        }

        val ready = item.isReadyForSync
        val marked = importService.isMarkedForSync(item)
        val icon = if (marked) VaadinIcon.MINUS.create() else VaadinIcon.PLUS.create()
        icon.setSize("20px")
        icon.color = if (!ready) "lightgray" else if (marked) "red" else "green"
        val button = Button(icon)
        button.isEnabled = ready
        button.height = "20px"
        button.addClickListener {
            if (marked) {
                importService.markForSync(item)
            } else {
                importService.unmarkForSync(item)
            }
            treeGrid.getDataProvider().refreshItem(item)
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
