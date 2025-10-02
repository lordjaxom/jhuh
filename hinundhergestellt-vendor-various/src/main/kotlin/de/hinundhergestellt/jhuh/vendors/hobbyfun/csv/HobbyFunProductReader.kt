package de.hinundhergestellt.jhuh.vendors.hobbyfun.csv

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import java.io.InputStream
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.text.ParseException

fun readHobbyFunProducts(path: Path) =
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { readHobbyFunProducts(it) }

fun readHobbyFunProducts(inputStream: InputStream) =
    inputStream.bufferedReader(StandardCharsets.UTF_8).use { readHobbyFunProducts(it) }

fun readHobbyFunProducts(reader: Reader) =
    csvReader(reader).use { readHobbyFunProducts(it) }

private fun readHobbyFunProducts(reader: CSVReader): List<HobbyFunProduct> {
    val header = reader.readNext()
    return reader.readAll().mapIndexed { index, row ->
        try {
            mapToHobbyFunProduct(header, row)
        } catch (e: ParseException) {
            throw ParseException(e.message + " at row $index", e.errorOffset).apply { initCause(e) }
        }
    }
}

private fun mapToHobbyFunProduct(header: Array<String>, row: Array<String>) =
    HobbyFunProduct(
        articleNumber = row[header.indexOf("\uFEFFArt.-Nr.")],
        description = row[header.indexOf("Art.-Bezeichnung")].trim(),
        ean = row[header.indexOf("EAN-Nummer")],
        imageUrl = row[header.indexOf("Foto")]
    )

private fun csvReader(reader: Reader) =
    CSVReaderBuilder(reader)
        .withCSVParser(csvParser())
        .build()

private fun csvParser() =
    CSVParserBuilder()
        .withSeparator(';')
        .build()
