package de.hinundhergestellt.jhuh.usecases.labels

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.ArticleComboBoxFactory
import de.hinundhergestellt.jhuh.components.CountTextField
import de.hinundhergestellt.jhuh.components.GridActionButton
import de.hinundhergestellt.jhuh.components.addActionsColumn
import de.hinundhergestellt.jhuh.components.addCountColumn
import de.hinundhergestellt.jhuh.components.addTextColumn

@Route
@PageTitle("Etiketten erstellen")
class LabelGeneratorView(
    private val service: LabelGeneratorService,
    articleComboBoxFactory: ArticleComboBoxFactory
) : VerticalLayout() {

    private val formatComboBox = ComboBox<String>()
    private val barcodesButton = Button()
    private val articleComboBox = articleComboBoxFactory()
    private val countTextField = CountTextField()
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
        barcodesButton.addClickListener { ui.get().page.open("/api/labels/${formatComboBox.value}/barcode", "_blank") }

        val layout = HorizontalLayout(formatComboBox, barcodesButton)
        layout.justifyContentMode = FlexComponent.JustifyContentMode.END
        layout.setWidthFull()
        add(layout)
    }

    private fun configureInputs() {
        articleComboBox.addValueChangeListener { validateInputs(); countTextField.focus() }
        articleComboBox.focus()

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
        labelsGrid.addTextColumn("Hersteller", flexGrow = 5) { it.vendor }
        labelsGrid.addTextColumn("Bezeichnung", flexGrow = 20) { it.name }
        labelsGrid.addTextColumn("Variante", flexGrow = 10) { it.variant }
        labelsGrid.addTextColumn("Barcode", flexGrow = 5) { it.barcode }
        labelsGrid.addCountColumn { it.count }
        labelsGrid.addActionsColumn { GridActionButton(VaadinIcon.TRASH) { removeLabel(it) } }
        labelsGrid.setItems(service.labels)
        labelsGrid.setSizeFull()
        labelsGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
        add(labelsGrid)
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
        countTextField.value = ""
        articleComboBox.focus()
    }

    private fun removeLabel(label: Label) {
        service.labels.remove(label)
        validateActions()
        labelsGrid.dataProvider.refreshAll()
    }
}