package de.hinundhergestellt.jhuh.service.ready2order

import de.hinundhergestellt.jhuh.vendors.ready2order.ArtooProductGroup

class GroupArtooMappedProduct internal constructor(
    private val group: ArtooProductGroup,
    variations: List<ArtooMappedVariation>
) : ArtooMappedProduct(variations) {

    override val id = "group-${group.id}"
    override val name by group::name
    override val description by group::description
}
