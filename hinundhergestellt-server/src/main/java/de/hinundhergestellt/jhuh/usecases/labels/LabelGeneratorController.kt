package de.hinundhergestellt.jhuh.usecases.labels

import de.hinundhergestellt.jhuh.components.Article
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooDataStore
import jakarta.servlet.http.HttpServletResponse
import org.krysalis.barcode4j.impl.upcean.EAN13Bean
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider
import org.krysalis.barcode4j.tools.MimeTypes
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView
import java.awt.image.BufferedImage

@RestController
@RequestMapping("api/labels")
class LabelGeneratorController(
    private val service: LabelGeneratorService,
    private val artooDataStore: ArtooDataStore
) {
    private val generator = EAN13Bean().apply { height = 8.0 }

    @GetMapping("{format}/{type}", produces = [MediaType.TEXT_HTML_VALUE])
    fun getLabels(@PathVariable format: String, @PathVariable type: String): ModelAndView {
        val labels = service.labels.flatMap { generateSequence { it }.take(it.count) }
        return ModelAndView(format, mapOf("labels" to labels, "type" to type))
    }

    @GetMapping("barcode/{barcode}", produces = [MediaType.IMAGE_PNG_VALUE])
    fun getBarcode(@PathVariable barcode: String, response: HttpServletResponse) {
        response.outputStream.use {
            val canvas = BitmapCanvasProvider(it, MimeTypes.MIME_PNG, 150, BufferedImage.TYPE_BYTE_BINARY, false, 0)
            generator.generateBarcode(canvas, barcode)
            canvas.finish()
        }
    }

    @GetMapping("prices", produces = [MediaType.TEXT_HTML_VALUE])
    fun getPrices(): ModelAndView {
        val labels = artooDataStore.findAllProducts().asSequence()
//            .filter { it.name.startsWith("myboshi") || it.name.startsWith("Gründl") }
//            .filter {
//                !it.name.contains("Dejavu") && !it.name.contains("Hot Socks") && !it.name.contains("Luminosa") &&
//                        !it.name.contains("nadel", ignoreCase = true) && !it.name.contains("watte")
//            }
            .filter { it.name == "myboshi No.2" }
            .flatMap { it.variations.asSequence().map { variation -> Article(it, variation) } }
            .filter { it.variation.name in listOf("smaragd", "titangrau", "tomate", "türkis", "weiß") }
            .map { ArticleLabel(it, null, 1) }
            .toList()
        val empty = generateSequence { EmptyLabel(1) }.take(54).toList()
        return ModelAndView("avery-zweckform-38x21", mapOf("labels" to (empty + labels), "type" to "price"))
    }
}