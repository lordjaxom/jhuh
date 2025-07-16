package de.hinundhergestellt.jhuh.backend.shoptexter

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shoptexter")
class ShopTexterController(
    private val service: ShopTexterService
) {

    @GetMapping("/category", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun generateCategoryDescription(
        @RequestParam(required = true) name: String,
        @RequestParam(required = true) tags: String
    ): String {
        val tagsAsSet = tags.splitToSequence(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return service.generateCategoryDescription(name, tagsAsSet).description
    }
}