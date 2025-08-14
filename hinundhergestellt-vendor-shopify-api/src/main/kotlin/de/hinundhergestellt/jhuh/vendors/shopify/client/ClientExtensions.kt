package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLResponse
import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.DisplayableError
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitSingle

suspend inline fun <reified PAYLOAD: Any?> WebClientGraphQLClient.executeQuery(
    request: String,
    queryName: String
): PAYLOAD {
    val response = executeWithRetry(request, "Query $queryName")
    return response.extractValueAsObject(queryName, PAYLOAD::class.java)
}

suspend inline fun <reified PAYLOAD: Any?> WebClientGraphQLClient.executeQuery(
    request: String
): PAYLOAD {
    val queryName = PAYLOAD::class.simpleName!!
        .replaceFirstChar { it.lowercase() }
        .let { if (it.endsWith("Connection")) it.removeSuffix("Connection") + "s" else it }
    return executeQuery(request, queryName)
}

suspend inline fun <reified PAYLOAD: Any> WebClientGraphQLClient.executeMutation(
    request: String,
    userErrors: (PAYLOAD) -> List<DisplayableError>
): PAYLOAD {
    val mutationName = PAYLOAD::class.simpleName!!.removeSuffix("Payload").replaceFirstChar { it.lowercase() }
    val response = executeWithRetry(request, "Mutation $mutationName")
    val payload = response.extractValueAsObject(mutationName, PAYLOAD::class.java)
    userErrors(payload).also { require(it.isEmpty()) { "Mutation $mutationName failed: ${it.map { error -> error.message }}" } }
    return payload
}

suspend inline fun WebClientGraphQLClient.executeWithRetry(
    request: String,
    label: String,
    maxRetries: Int = 5
): GraphQLResponse {
    var attempt = 0
    while (attempt < maxRetries) {
        val response = reactiveExecuteQuery(request).awaitSingle()
        if (!response.hasErrors()) return response

        val throttled = response.errors.any { it.message == "Throttled" }
        if (!throttled) throw IllegalStateException("$label failed: ${response.errors}")

        delay(2000)
        attempt++
    }
    throw IllegalStateException("$label failed: Still throttled after $attempt tries")
}