package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.DisplayableError
import kotlinx.coroutines.reactive.awaitSingle

suspend inline fun <reified PAYLOAD: Any> WebClientGraphQLClient.executeMutation(
    request: String,
    userErrors: (PAYLOAD) -> List<DisplayableError>
): PAYLOAD {
    val mutationName = PAYLOAD::class.simpleName!!.removeSuffix("Payload").replaceFirstChar { it.lowercase() }
    val response = reactiveExecuteQuery(request).awaitSingle()
    require(!response.hasErrors()) { "Mutation $mutationName failed: ${response.errors}" }
    val payload = response.extractValueAsObject(mutationName, PAYLOAD::class.java)
    userErrors(payload).also { require(it.isEmpty()) { "$mutationName failed: ${it.map { error -> error.message }}" } }
    return payload
}