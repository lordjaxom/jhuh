package de.hinundhergestellt.jhuh.usecases.incoming

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.PageTitle
import com.vaadin.flow.router.Route
import de.hinundhergestellt.jhuh.components.ArticleComboBox
import de.hinundhergestellt.jhuh.components.ArticleComboBoxService
import de.hinundhergestellt.jhuh.components.CountTextField
import de.hinundhergestellt.jhuh.components.GridActionButton
import de.hinundhergestellt.jhuh.components.actionsColumn
import de.hinundhergestellt.jhuh.components.countColumn
import de.hinundhergestellt.jhuh.components.textColumn

@Route
@PageTitle("Wareneingang")
class IncomingGoodsView(
    private val service: IncomingGoodsService,
    articleComboBoxService: ArticleComboBoxService,
): VerticalLayout() {

    private val saveButton = Button()
    private val articleComboBox = ArticleComboBox(articleComboBoxService)
    private val countTextField = CountTextField()
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
        incomingGrid.textColumn("Artikel", flexGrow = 20) { it.label }
        incomingGrid.countColumn { it.count }
        incomingGrid.actionsColumn { GridActionButton(VaadinIcon.TRASH) { removeIncoming(it) } }
        incomingGrid.setItems(service.incomings)
        incomingGrid.setSizeFull()
        incomingGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES)
        add(incomingGrid)
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

    private fun removeIncoming(incoming: Incoming) {
        service.incomings.remove(incoming)
        validateActions()
        incomingGrid.dataProvider.refreshAll()
    }
}