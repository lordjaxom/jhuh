package de.hinundhergestellt.jhuh.vendors.rayher.datastore

import de.hinundhergestellt.jhuh.vendors.rayher.csv.readRayherProducts
import org.springframework.stereotype.Service
import kotlin.io.path.Path

@Service
class RayherDataStore {

    val products = readRayherProducts(Path("var/Rayher0725d_Excel.csv"))

    fun findByEan(ean: String) = products.find { it.ean == ean }
}