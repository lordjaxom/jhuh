package de.hinundhergestellt.jhuh.usecases.labels

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
    private val service: LabelGeneratorService
) {
    private val generator = EAN13Bean().apply { height = 8.0 }

    @GetMapping("{format}", produces = [MediaType.TEXT_HTML_VALUE])
    fun getLabels(@PathVariable format: String): ModelAndView {
        val labels = service.labels.flatMap { generateSequence { it }.take(it.count) }
        return ModelAndView(format, "labels", labels)
    }

    @GetMapping("barcode/{barcode}", produces = [MediaType.IMAGE_PNG_VALUE])
    fun getBarcode(@PathVariable barcode: String, response: HttpServletResponse) {
        response.outputStream.use {
            val canvas = BitmapCanvasProvider(it, MimeTypes.MIME_PNG, 150, BufferedImage.TYPE_BYTE_BINARY, false, 0)
            generator.generateBarcode(canvas, barcode)
            canvas.finish()
        }
    }
}