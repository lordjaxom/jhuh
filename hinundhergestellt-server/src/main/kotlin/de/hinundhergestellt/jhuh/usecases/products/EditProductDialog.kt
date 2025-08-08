package de.hinundhergestellt.jhuh.usecases.products

import com.vaadin.flow.component.Key
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.data.binder.ValidationException
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.components.bind
import de.hinundhergestellt.jhuh.components.binder
import de.hinundhergestellt.jhuh.components.button
import de.hinundhergestellt.jhuh.components.column
import de.hinundhergestellt.jhuh.components.footer
import de.hinundhergestellt.jhuh.components.formLayout
import de.hinundhergestellt.jhuh.components.header
import de.hinundhergestellt.jhuh.components.richTextEditor
import de.hinundhergestellt.jhuh.components.row
import de.hinundhergestellt.jhuh.components.textField
import de.hinundhergestellt.jhuh.components.toProperty
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct

private class EditProductDialog(
    private val artooProduct: ArtooMappedProduct,
    private val syncProduct: SyncProduct?,
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
            setAutoResponsive(true)
            isExpandColumns = true
            isExpandFields = true

            header("Stammdaten aus ready2order")
            row {
                textField("Name") {
                    bind(artooBinder)
                        .asRequired("Name darf nicht leer sein.")
                        .toProperty(ArtooMappedProduct::name)
                    focus()
                }
                column(colspan = 2) {
                    textField("Beschreibung (Titel in Shopify)") {
                        bind(artooBinder)
                            .asRequired("Beschreibung darf nicht leer sein.")
                            .toProperty(ArtooMappedProduct::description)
                    }
                }
            }
        }
        formLayout {
            setAutoResponsive(true)
            isExpandColumns = true
            isExpandFields = true
            style.setMarginTop("var(--lumo-space-m)")

            header("Zusätzliche Informationen für Shopify")
            richTextEditor {
                height = "15em"
                themeNames += "compact"
                bind(syncBinder)
                    .toProperty(SyncProduct::descriptionHtml)
            }
        }
        footer {
            button("Speichern") {
                addThemeVariants(ButtonVariant.LUMO_PRIMARY)
                addClickShortcut(Key.ENTER)
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

suspend fun editProduct(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct?) =
    suspendableDialog { EditProductDialog(artooProduct, syncProduct, it) }