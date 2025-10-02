package de.hinundhergestellt.jhuh.vendors.google.taxonomy

import de.hinundhergestellt.jhuh.core.loadTextResource

object GoogleCategoryTaxonomyProvider {

    val categories = loadTextResource { "/google-categories.txt" }.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split(" - ") }
        .map { (id, context) -> GoogleCategory(id.toInt(), context.split(" > ").last(), context) }
        .associateBy { it.id }
}

data class GoogleCategory(
    val id: Int,
    val name: String,
    val context: String
)