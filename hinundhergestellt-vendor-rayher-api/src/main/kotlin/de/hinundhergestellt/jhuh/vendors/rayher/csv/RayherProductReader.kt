package de.hinundhergestellt.jhuh.vendors.rayher.csv

import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.InputStream
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

fun readRayherProducts(path: Path) =
    Files.newBufferedReader(path, StandardCharsets.UTF_8).use { readRayherProducts(it) }

fun readRayherProducts(inputStream: InputStream) =
    inputStream.bufferedReader(StandardCharsets.UTF_8).use { readRayherProducts(it) }

fun readRayherProducts(reader: Reader) =
    CSVReaderBuilder(reader)
        .withSkipLines(1)
        .withCSVParser(
            CSVParserBuilder()
                .withSeparator(',')
                .build()
        )
        .build()
        .use { reader ->
            reader.readAll().map { row ->
                RayherProduct(
                    supplierId = row[0].trim(),
                    articleNumber = row[1].trim(),
                    description= row[2].trim(),
                    ean = row[3].trim(),
                    descriptions = row.drop(24).take(3).map { it.trim() }.filter { it.isNotBlank() },
                    imageUrls = row.drop(40).map { it.trim() }.filter { it.isNotBlank() }
                )
            }
        }

