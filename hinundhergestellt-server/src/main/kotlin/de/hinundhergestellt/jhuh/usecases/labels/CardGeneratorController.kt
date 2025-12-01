package de.hinundhergestellt.jhuh.usecases.labels

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView
import kotlin.collections.mapOf

@RestController
@RequestMapping("api/cards")
class CardGeneratorController {

    @GetMapping("{type}", produces = [MediaType.TEXT_HTML_VALUE])
    fun getCards(@PathVariable type: String): ModelAndView {
        val cards = generateSequence { Card("ADVENTSZAUBER25") }.take(6).toList()
        return ModelAndView("goodie-cards-$type", mapOf("cards" to cards))
    }
}

class Card(
    val code: String,
)