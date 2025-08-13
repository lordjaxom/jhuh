package de.hinundhergestellt.jhuh.backend.mapping

data class MappingProblem(
    val message: String,
    val error: Boolean
)

fun List<MappingProblem>.hasErrors() = any { it.error }
fun List<MappingProblem>.toPresentationString() = joinToString("\n") { it.message }