package de.hinundhergestellt.jhuh.backend.shoptexter.model

import com.fasterxml.jackson.annotation.JsonIgnore
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProductGroup

abstract class ArtooMappedProductForAiMixin {

    @get:JsonIgnore
    abstract val barcodes: List<String>

    @get:JsonIgnore
    abstract val `group$hinundhergestellt_server`: ArtooProductGroup

    @get:JsonIgnore
    abstract val `product$hinundhergestellt_server`: ArtooProduct
}