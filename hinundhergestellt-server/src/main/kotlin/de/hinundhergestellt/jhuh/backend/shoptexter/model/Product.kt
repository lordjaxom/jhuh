package de.hinundhergestellt.jhuh.backend.shoptexter.model

class Product(
    val name: String,
    val title: String,
    val description: String,
    val productType: String,
    val vendor: String,
    val tags: Set<String>,
    val technicalDetails: Map<String, String>,
    val hasOnlyDefaultVariant: Boolean,
    val variants: List<String>
)