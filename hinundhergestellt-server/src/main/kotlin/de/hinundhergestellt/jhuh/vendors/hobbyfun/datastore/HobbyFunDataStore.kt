package de.hinundhergestellt.jhuh.vendors.hobbyfun.datastore

import de.hinundhergestellt.jhuh.vendors.hobbyfun.csv.readHobbyFunProducts
import org.springframework.stereotype.Service
import kotlin.io.path.Path

@Service
class HobbyFunDataStore {

    val products = readHobbyFunProducts(Path("var/hobbyfun-artikelstamm.csv"))

    fun findByEan(ean: String) = products.find { it.ean == ean }
}