package de.hinundhergestellt.jhuh.usecases.incoming

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
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
import de.hinundhergestellt.jhuh.components.ArticleComboBoxFactory

@Route
@PageTitle("Wareneingang")
class IncomingGoodsView(
    private val service: IncomingGoodsService,
    articleComboBoxFactory: ArticleComboBoxFactory,
): VerticalLayout() {

    private val saveButton = Button()
    private val articleComboBox = articleComboBoxFactory()
    private val countTextField = TextField()
    private val addButton = Button()
    private val incomingGrid = Grid<Incoming>()

    init {
        setSizeFull()
        width = "1170px"
        style.setMargin("0 auto")

        configureHeader()
        configureInputs()
        configureIncomingGrid()
    }

    private fun configureHeader() {
        saveButton.text = "An ready2order senden"
        saveButton.isEnabled = false
        saveButton.addClickListener {  }

        val layout = HorizontalLayout(saveButton)
        layout.justifyContentMode = FlexComponent.JustifyContentMode.END
        layout.setWidthFull()
        add(layout)
    }

    private fun configureInputs() {
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
        addButton.addClickListener { createIncoming() }

        val layout = HorizontalLayout(articleComboBox, countTextField, addButton)
        layout.alignItems = FlexComponent.Alignment.END
        layout.setWidthFull()
        add(layout)
    }

    private fun configureIncomingGrid() {
        incomingGrid.addColumn { it.label }
            .setHeader("Artikel")
            .apply {
                isSortable = false
                flexGrow = 20
            }
        incomingGrid.addColumn { it.count }
            .setHeader("#")
            .apply {
                isSortable = false
                textAlign = ColumnTextAlign.END
                width = "4em"
                flexGrow = 0
            }
        incomingGrid.addComponentColumn { incomingItemActionsColumn(it) }
            .setHeader("")
            .apply {
                isSortable = false
                width = "48px"
                flexGrow = 0
            }
        incomingGrid.setItems(service.incomings)
        incomingGrid.setSizeFull()
        incomingGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
        add(incomingGrid)
    }

    private fun incomingItemActionsColumn(incoming: Incoming): Component {
        val deleteIcon = VaadinIcon.TRASH.create()
        deleteIcon.setSize("20px")
        val deleteButton = Button(deleteIcon)
        deleteButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
        deleteButton.addClickListener {
            service.incomings.remove(incoming)
            validateActions()
            incomingGrid.dataProvider.refreshAll()
        }
        return deleteButton
    }

    private fun validateActions() {
        saveButton.isEnabled = service.incomings.isNotEmpty()
    }

    private fun validateInputs() {
        val articleSelected = articleComboBox.value != null
        val countValid = countTextField.value.toIntOrNull(10)?.let { it > 0 } ?: false
        addButton.isEnabled = articleSelected && countValid
    }

    private fun createIncoming() {
        service.createIncoming(articleComboBox.value, countTextField.value.toInt(10))
        validateActions()
        incomingGrid.dataProvider.refreshAll()

        articleComboBox.value = null
        countTextField.value = ""
        articleComboBox.focus()
    }
}