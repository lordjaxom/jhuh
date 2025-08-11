package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.formlayout.FormLayout
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.data.binder.ValidationException
import com.vaadin.flow.dom.Style
import de.hinundhergestellt.jhuh.backend.mapping.MappingService
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncTechnicalDetail
import de.hinundhergestellt.jhuh.backend.syncdb.SyncVendor
import de.hinundhergestellt.jhuh.components.ReorderableGridField
import de.hinundhergestellt.jhuh.components.bind
import de.hinundhergestellt.jhuh.components.binder
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.comboBox
import de.hinundhergestellt.jhuh.components.div
import de.hinundhergestellt.jhuh.components.footer
import de.hinundhergestellt.jhuh.components.formLayout
import de.hinundhergestellt.jhuh.components.header
import de.hinundhergestellt.jhuh.components.htmlEditor
import de.hinundhergestellt.jhuh.components.itemLabelGenerator
import de.hinundhergestellt.jhuh.components.lightHeaderDiv
import de.hinundhergestellt.jhuh.components.reorderableGridField
import de.hinundhergestellt.jhuh.components.setColspan
import de.hinundhergestellt.jhuh.components.tagTextField
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.toProperty
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct

private class EditProductDialog(
    private val artooProduct: ArtooMappedProduct,
    private val syncProduct: SyncProduct?,
    private val mappingService: MappingService,
    private val callback: (EditProductResult?) -> Unit
) : Dialog() {

    private val artooBinder = binder<ArtooMappedProduct>()
    private val syncBinder = binder<SyncProduct>()

    init {
        width = "1000px"
        headerTitle = "Produkt bearbeiten"

        header {
            button(VaadinIcon.CLOSE) {
                addThemeVariants(ButtonVariant.LUMO_TERTIARY)
                addClickShortcut(Key.ESCAPE)
                addClickListener { close(); callback(null) }
            }
        }

        formLayout {
            setResponsiveSteps(
                FormLayout.ResponsiveStep("0", 1),
                FormLayout.ResponsiveStep("900px", 3)
            )

            lightHeaderDiv("Stammdaten aus ready2order") {
                this@formLayout.setColspan(3)
                style.setAlignSelf(Style.AlignSelf.FLEX_START)
            }
            textField("Name (Kassenoberfläche)") {
                this@formLayout.setColspan(1)
                bind(artooBinder)
                    .asRequired("Name darf nicht leer sein.")
                    .toProperty(ArtooMappedProduct::name)
                focus()
            }
            textField("Beschreibung (Titel in Shopify)") {
                this@formLayout.setColspan(2)
                bind(artooBinder)
                    .asRequired("Beschreibung darf nicht leer sein.")
                    .toProperty(ArtooMappedProduct::description)
            }
        }

        formLayout {
            setResponsiveSteps(
                FormLayout.ResponsiveStep("0", 1),
                FormLayout.ResponsiveStep("900px", 3)
            )

            lightHeaderDiv("Zusätzliche Informationen für Shopify") {
                this@formLayout.setColspan(3)
                style.setAlignSelf(Style.AlignSelf.FLEX_START)
            }
            div {
                this@formLayout.setColspan(2)
                style.setAlignSelf(Style.AlignSelf.FLEX_START)

                htmlEditor("Produktbeschreibung") {
                    height = "10em"
                    bind(syncBinder).toProperty(SyncProduct::descriptionHtml)
                }
                reorderableGridField("Technische Daten") {
                    height = "10em"
                    setWidthFull()
                    bind(syncBinder).bind(
                        { it.technicalDetails.map { item -> ReorderableGridField.Item(item.name, item.value) } },
                        { target, value ->
                            target.technicalDetails.clear()
                            target.technicalDetails += value.mapIndexed { index, item -> SyncTechnicalDetail(item.name, item.value, index) }
                        }
                    )
                }
            }
            div {
                this@formLayout.setColspan(1)
                style.setAlignSelf(Style.AlignSelf.FLEX_START)

                comboBox<SyncVendor>("Hersteller") {
                    isClearButtonVisible = true
                    itemLabelGenerator { it.name }
                    setWidthFull()
                    setItems(mappingService.vendors)
                    bind(syncBinder)
                        .asRequired("Hersteller darf nicht leer sein.")
                        .toProperty(SyncProduct::vendor)
                }
                textField("Produktart") {
                    setWidthFull()
                    bind(syncBinder)
                        .asRequired("Produktart darf nicht leer sein.")
                        .toProperty(SyncProduct::type)
                }
                tagTextField("Vererbte Tags") {
                    setWidthFull()
                    isReadOnly = true
                    value = syncProduct?.let { mappingService.inheritedTags(it, artooProduct).toMutableSet() }
                }
                tagTextField("Weitere Tags") {
                    setWidthFull()
                    bind(syncBinder).toProperty(SyncProduct::tags)
                }
            }
        }
        footer {
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickListener { save() }
            }
        }

        artooBinder.readBean(artooProduct)
        syncBinder.readBean(syncProduct)
    }

    private fun save() {
        try {
            val artoo = if (artooBinder.hasChanges()) artooProduct.also { artooBinder.writeBean(it) } else null
            val sync = if (syncBinder.hasChanges()) syncProduct.also { syncBinder.writeBean(it) } else null
            close()
            callback(EditProductResult(artoo, sync))
        } catch (_: ValidationException) {
        }
    }
}

class EditProductResult(
    val artoo: ArtooMappedProduct?,
    val sync: SyncProduct?
)

suspend fun editProduct(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct?, mappingService: MappingService) =
    suspendableDialog { EditProductDialog(artooProduct, syncProduct, mappingService, it) }