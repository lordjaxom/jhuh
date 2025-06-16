package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.codegen.BaseSubProjectionNode
import com.netflix.graphql.dgs.client.codegen.GraphQLQuery
import com.netflix.graphql.dgs.client.codegen.GraphQLQueryRequest
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.DisplayableError

inline fun <reified PAYLOAD: Any> GraphQLClient.executeMutation(
    query: GraphQLQuery,
    root: BaseSubProjectionNode<*, *>,
    userErrors: (PAYLOAD) -> List<DisplayableError>
): PAYLOAD {
    val queryName = query::class.simpleName!!.removeSuffix("GraphQLQuery")
    val request = GraphQLQueryRequest(query, root)
    val response = executeQuery(request.serialize())
    require(!response.hasErrors()) { "$queryName failed: ${response.errors}" }
    val payload = response.extractValueAsObject(queryName.replaceFirstChar { it.lowercase() }, PAYLOAD::class.java)
    userErrors(payload).also { require(it.isEmpty()) { "$queryName failed: $it" } }
    return payload
}

inline fun <reified PAYLOAD: Any> GraphQLClient.executeMutation(
    request: String,
    userErrors: (PAYLOAD) -> List<DisplayableError>
): PAYLOAD {
    val mutationName = PAYLOAD::class.simpleName!!.removeSuffix("Payload").replaceFirstChar { it.lowercase() }
    val response = executeQuery(request)
    require(!response.hasErrors()) { "Mutation $mutationName failed: ${response.errors}" }
    val payload = response.extractValueAsObject(mutationName, PAYLOAD::class.java)
    userErrors(payload).also { require(it.isEmpty()) { "$mutationName failed: $it" } }
    return payload
}