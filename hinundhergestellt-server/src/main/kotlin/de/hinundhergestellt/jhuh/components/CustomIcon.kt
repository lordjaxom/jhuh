package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.icon.SvgIcon
import com.vaadin.flow.server.streams.DownloadHandler

enum class CustomIcon {

    CATEGORY,
    PRODUCT,
    SHOPIFY,
    VARIATION;

    fun create() = SvgIcon(DownloadHandler.forClassResource(CustomIcon::class.java, "icon_${name.lowercase()}.svg"))
}