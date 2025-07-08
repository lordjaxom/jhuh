package de.hinundhergestellt.jhuh.vendors.rayher.csv

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

private val bigDecimalParser = (NumberFormat.getInstance(Locale.GERMAN) as DecimalFormat).apply { isParseBigDecimal = true }

fun readRayherProducts(path: Path) =
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { readRayherProducts(it) }

fun readRayherProducts(inputStream: InputStream) =
    inputStream.bufferedReader(StandardCharsets.UTF_8).use { readRayherProducts(it) }

fun readRayherProducts(reader: Reader) =
    csvReader(reader).use { readRayherProducts(it) }

private fun readRayherProducts(reader: CSVReader): List<RayherProduct> {
    val header = reader.readNext()
    return reader.readAll().map { mapToRayherProduct(header, it) }
}

private fun mapToRayherProduct(header: Array<String>, row: Array<String>): RayherProduct {
    return RayherProduct(
        articleNumber = row[header.indexOf("Artikelnr.")].trim(),
        description = row[header.indexOf("Bezeichnung")].trim(),
        ean = row[header.indexOf("EAN")].trim(),
        descriptions = sequenceOf("Bezeichnung1", "Bezeichnung2", "Bezeichnung3")
            .map { row[header.indexOf(it)].trim() }
            .filter { it.isNotBlank() }
            .toList(),
        weight = bigDecimalParser.parse(row[header.indexOf("Gewicht PE g")]) as BigDecimal,
        imageUrls = row.asSequence()
            .drop(header.indexOf("URL_Bild"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList(),
    )
}

private fun csvReader(reader: Reader) =
    CSVReaderBuilder(reader)
        .withCSVParser(csvParser())
        .build()

private fun csvParser() =
    CSVParserBuilder()
        .withSeparator(',')
        .build()