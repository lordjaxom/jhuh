package de.hinundhergestellt.jhuh.usecases.labels

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.data.provider.DataProvider
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.ArticleComboBox
import de.hinundhergestellt.jhuh.components.ArticleComboBoxService
import de.hinundhergestellt.jhuh.components.CountTextField
import de.hinundhergestellt.jhuh.components.GridActionButton
import de.hinundhergestellt.jhuh.components.actionsColumn
import de.hinundhergestellt.jhuh.components.articleComboBox
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.comboBox
import de.hinundhergestellt.jhuh.components.countColumn
import de.hinundhergestellt.jhuh.components.countTextField
import de.hinundhergestellt.jhuh.components.grid
import de.hinundhergestellt.jhuh.components.horizontalLayout
import de.hinundhergestellt.jhuh.components.textColumn

@Route
@PageTitle("Etiketten erstellen")
class LabelGeneratorView(
    private val service: LabelGeneratorService,
    articleComboBoxService: ArticleComboBoxService
) : VerticalLayout() {

    private val formatComboBox: ComboBox<String>
    private val barcodesButton: Button
    private val articleComboBox: ArticleComboBox
    private val countTextField: CountTextField
    private val addButton: Button

    private val labelsDataProvider: DataProvider<Label, *>

    init {
        setSizeFull()
        width = "1170px"
        style.setMargin("0 auto")

        horizontalLayout {
            justifyContentMode = FlexComponent.JustifyContentMode.END
            setWidthFull()

            formatComboBox = comboBox<String> {
                width = "20em"
                setItems("avery-zweckform-38x21", "avery-zweckform-49x25")
                value = "avery-zweckform-38x21"
                addValueChangeListener { validateActions() }
            }
            barcodesButton = button("Barcodes erstellen") {
                addClickListener { ui.get().page.open("/api/labels/${formatComboBox.value}/barcode", "_blank") }
            }
        }
        horizontalLayout {
            alignItems = FlexComponent.Alignment.END
            setWidthFull()

            articleComboBox = articleComboBox(articleComboBoxService) {
                focus()
            }
            countTextField = countTextField() {
                addValueChangeListener { validateInputs() }
            }
            addButton = button("Hinzufügen") {
                isEnabled = false
                addClickShortcut(Key.ENTER)
                addClickListener { createLabel() }
            }

            articleComboBox.addValueChangeListener { validateInputs(); countTextField.focus() }
        }
        grid<Label> {
            textColumn("Hersteller", flexGrow = 5) { it.vendor }
            textColumn("Bezeichnung", flexGrow = 20) { it.name }
            textColumn("Variante", flexGrow = 10) { it.variant }
            textColumn("Barcode", flexGrow = 5) { it.barcode }
            countColumn { it.count }
            actionsColumn { GridActionButton(VaadinIcon.TRASH) { removeLabel(it) } }
            setItems(service.labels)
            setSizeFull()
            addThemeVariants(GridVariant.LUMO_ROW_STRIPES)

            labelsDataProvider = dataProvider
        }
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
        labelsDataProvider.refreshAll()

        articleComboBox.value = null
        countTextField.value = ""
        articleComboBox.focus()
    }

    private fun removeLabel(label: Label) {
        service.labels.remove(label)
        validateActions()
        labelsDataProvider.refreshAll()
    }
}