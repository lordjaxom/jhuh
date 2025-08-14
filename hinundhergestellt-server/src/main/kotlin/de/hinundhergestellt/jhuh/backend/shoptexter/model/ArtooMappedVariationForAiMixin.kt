package de.hinundhergestellt.jhuh.backend.shoptexter.model

import com.fasterxml.jackson.annotation.JsonIgnore
import de.hinundhergestellt.jhuh.vendors.ready2order.client.ArtooProduct
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct

abstract class ArtooMappedVariationForAiMixin {

    @get:JsonIgnore
    abstract val parent: ArtooMappedProduct

    @get:JsonIgnore
    abstract val `product$hinundhergestellt_server`: ArtooProduct
}