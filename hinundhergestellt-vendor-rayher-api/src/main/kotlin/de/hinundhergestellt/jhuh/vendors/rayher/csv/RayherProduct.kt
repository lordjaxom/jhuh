package de.hinundhergestellt.jhuh.vendors.rayher.csv

data class RayherProduct(
    val supplierId: String,
    val articleNumber: String,
    val description: String,
    val ean: String,
    val descriptions: List<String>,
    val imageUrls: List<String>
)
