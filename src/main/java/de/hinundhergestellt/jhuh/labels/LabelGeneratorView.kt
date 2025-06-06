package de.hinundhergestellt.jhuh.labels

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.ItemLabelGenerator
import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.grid.ColumnTextAlign
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.component.textfield.TextFieldVariant
import com.vaadin.flow.data.value.ValueChangeMode
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import kotlin.streams.asStream

@Route
@PageTitle("Etiketten erstellen")
class LabelGeneratorView(
    private val service: LabelGeneratorService,
) : VerticalLayout() {

    private val formatComboBox = ComboBox<String>()
    private val barcodesButton = Button()
    private val articleComboBox = ComboBox<Article>()
    private val countTextField = TextField()
    private val addButton = Button()
    private val labelsGrid = Grid<Label>()

    init {
        setSizeFull()
        width = "1170px"
        style.setMargin("0 auto")

        configureHeader()
        configureInputs()
        configureLabelsGrid()
    }

    private fun configureHeader() {
        formatComboBox.width = "20em"
        formatComboBox.setItems("avery-zweckform-38x21", "avery-zweckform-49x25")
        formatComboBox.value = "avery-zweckform-38x21"
        formatComboBox.addValueChangeListener { validateActions() }

        barcodesButton.text = "Barcodes erstellen"
        barcodesButton.isEnabled = false
        barcodesButton.addClickListener { ui.get().page.open("/api/labels/${formatComboBox.value}", "_blank") }

        val layout = HorizontalLayout(formatComboBox, barcodesButton)
        layout.justifyContentMode = FlexComponent.JustifyContentMode.END
        layout.setWidthFull()
        add(layout)
    }

    private fun configureInputs() {
        articleComboBox.label = "Artikel"
        articleComboBox.placeholder = "Barcode oder Suchbegriff eingeben"
        articleComboBox.itemLabelGenerator = ItemLabelGenerator { it.label }
        articleComboBox.setWidthFull()
        articleComboBox.setScannerCompatibleData { filter, offset, limit -> fetchArticles(filter, offset, limit) }
        articleComboBox.addValueChangeListener { validateInputs(); countTextField.focus() }
        articleComboBox.focus()

        countTextField.label = "Anzahl"
        countTextField.placeholder = "0"
        countTextField.allowedCharPattern = "[0-9]"
        countTextField.maxLength = 5
        countTextField.valueChangeMode = ValueChangeMode.EAGER
        countTextField.isAutoselect = true
        countTextField.width = "5em"
        countTextField.addThemeVariants(TextFieldVariant.LUMO_ALIGN_RIGHT)
        countTextField.addValueChangeListener { validateInputs() }

        addButton.text = "Hinzufügen"
        addButton.isEnabled = false
        addButton.addClickShortcut(Key.ENTER)
        addButton.addClickListener { createLabel() }

        val layout = HorizontalLayout(articleComboBox, countTextField, addButton)
        layout.alignItems = FlexComponent.Alignment.END
        layout.setWidthFull()
        add(layout)
    }

    private fun configureLabelsGrid() {
        labelsGrid.addColumn { it.vendor }
            .setHeader("Hersteller")
            .apply {
                isSortable = false
                flexGrow = 5
            }
        labelsGrid.addColumn { it.name }
            .setHeader("Bezeichnung")
            .apply {
                isSortable = false
                flexGrow = 20
            }
        labelsGrid.addColumn { it.variant }
            .setHeader("Variante")
            .apply {
                isSortable = false
                flexGrow = 10
            }
        labelsGrid.addColumn { it.barcode }
            .setHeader("Barcode")
            .apply {
                isSortable = false
                flexGrow = 5
            }
        labelsGrid.addColumn { it.count }
            .setHeader("#")
            .apply {
                isSortable = false
                textAlign = ColumnTextAlign.END
                width = "4em"
                flexGrow = 0
            }
        labelsGrid.addComponentColumn { labelsItemActionsColumn(it) }
            .setHeader("")
            .apply {
                isSortable = false
                width = "48px"
                flexGrow = 0
            }
        labelsGrid.setItems(service.labels)
        labelsGrid.setSizeFull()
        labelsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
        add(labelsGrid)
    }

    private fun labelsItemActionsColumn(label: Label): Component {
        val deleteIcon = VaadinIcon.TRASH.create()
        deleteIcon.setSize("20px")
        val deleteButton = Button(deleteIcon)
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
        deleteButton.addClickListener {
            service.labels.remove(label)
            validateActions()
            labelsGrid.dataProvider.refreshAll()
        }
        return deleteButton
    }

    private fun validateActions() {
        barcodesButton.isEnabled = formatComboBox.value != null && service.labels.isNotEmpty()
    }

    private fun validateInputs() {
        val articleSelected = articleComboBox.value != null
        val countValid = countTextField.value.toIntOrNull(10)?.let { it > 0 } ?: false
        addButton.isEnabled = articleSelected && countValid
    }

    private fun createLabel() {
        service.createLabel(articleComboBox.value, countTextField.value.toInt(10))
        validateActions()
        labelsGrid.dataProvider.refreshAll()

        articleComboBox.value = null
        countTextField.value = "0"
        articleComboBox.focus()
    }

    private fun fetchArticles(filter: String?, offset: Int = 0, limit: Int = Int.MAX_VALUE): Sequence<Article> {
        if (filter.isNullOrBlank()) {
            return sequenceOf()
        }

        return service.articles
            .filter { it.filterBy(filter) }
            .drop(offset)
            .take(limit)
    }
}

fun <T : Any> ComboBox<T>.setScannerCompatibleData(fetchItems: (String, Int, Int) -> Sequence<T>) {
    setDataProvider(
        { filter, offset, limit -> fetchItems(filter, offset, limit).asStream() },
        { filter -> fetchItems(filter, 0, Int.MAX_VALUE).count() }
    )

    // @formatter:off
    element.executeJs("""
            const input = this.inputElement
            input.addEventListener('keydown', function(e) {
                if (e.key === 'Enter') {
                    this.dispatchEvent(new CustomEvent('enter-pressed', {
                            detail: { inputValue: input.value },
                            bubbles: true
                    }));
                }            
            });
            """.trimIndent())
    // @formatter:on

    val enterListener = element.addEventListener("enter-pressed") { event ->
        event.eventData.getString("event.detail.inputValue")
            ?.takeIf { it.toULongOrNull(10) != null }
            ?.let { fetchItems(it, 0, 1).firstOrNull() }
            ?.also {
                isOpened = false
                value = it
            }
    }
    enterListener.addEventData("event.detail.inputValue")
}
