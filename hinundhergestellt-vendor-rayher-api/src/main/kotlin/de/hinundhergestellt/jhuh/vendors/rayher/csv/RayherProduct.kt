package de.hinundhergestellt.jhuh.vendors.rayher.csv

import java.math.BigDecimal

data class RayherProduct(
    val articleNumber: String,
    val description: String,
    val ean: String,
    val descriptions: List<String>,
    val weight: BigDecimal?,
    val imageUrls: List<String>
)
