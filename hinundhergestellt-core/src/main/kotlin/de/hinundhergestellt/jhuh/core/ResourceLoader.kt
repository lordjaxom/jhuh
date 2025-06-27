package de.hinundhergestellt.jhuh.core

import java.nio.charset.StandardCharsets

fun loadTextResource(resource: () -> String): String {
    val contextClass = resource.javaClass
    val resourceName = resource()
    return contextClass.getResource(resourceName)!!.readText(StandardCharsets.UTF_8)
}