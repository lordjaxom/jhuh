package de.hinundhergestellt.jhuh.labels

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
class LabelsController {

    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun getLabels(): ModelAndView {
        val labels = List(30) { LabelsModel("SUPERIOR", "Jewel metallic", "95JBK metallic black", "9567300692034") } +
                List(30) { LabelsModel("SUPERIOR", "Matt Chrome", "9271 light gold", "4167300692710") } +
                List(30) { LabelsModel("POLI-FLEX TURBO", "4905 Navy Blue", "", "4251148208766") }
        return ModelAndView("avery-zweckform-49x25", "labels", labels)
    }

    @GetMapping("barcode/{barcode}", produces = [MediaType.TEXT_HTML_VALUE])
    fun getBarcode(@PathVariable barcode: String, response: HttpServletResponse) {
        val generator = EAN13Bean()
        generator.height = 8.0
        val canvas = BitmapCanvasProvider(response.outputStream, MimeTypes.MIME_PNG, 150, BufferedImage.TYPE_BYTE_BINARY, false, 0)
        generator.generateBarcode(canvas, barcode)
        canvas.finish()
    }
}