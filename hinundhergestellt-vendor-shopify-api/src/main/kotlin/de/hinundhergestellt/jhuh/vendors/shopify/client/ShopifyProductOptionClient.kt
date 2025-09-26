package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.OptionUpdateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOptionUpdatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.ProductOptionsDeletePayload
import org.springframework.stereotype.Component

@Component
class ShopifyProductOptionClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient
) {
    suspend fun update(product: ShopifyProduct, option: ShopifyProductOption) {
        val request = buildMutation {
            productOptionUpdate(
                productId = product.id,
                option = option.toOptionUpdateInput(),
                optionValuesToUpdate = option.optionValues.map { it.toOptionValueUpdateInput() },
            ) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, ProductOptionUpdatePayload::userErrors)
    }

    suspend fun delete(product: ShopifyProduct, options: List<ShopifyProductOption>) {
        val request = buildMutation {
            productOptionsDelete(productId = product.id, options = options.map { it.id }) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, ProductOptionsDeletePayload::userErrors)
    }

    suspend fun createValues(product: ShopifyProduct, option: ShopifyProductOption, values: List<ShopifyProductOptionValue>) {
        val request = buildMutation {
            productOptionUpdate(
                productId = product.id,
                option = OptionUpdateInput(option.id),
                optionValuesToAdd = values.map { it.toOptionValueCreateInput() }
            ) {
                product { optionsForWrapper() }
                userErrors { message; field }
            }
        }

        val payload = shopifyGraphQLClient.executeMutation(request, ProductOptionUpdatePayload::userErrors)
        val changedOption = payload.product!!.options.first { it.id == option.id }
        values.forEach { value -> value.internalId = changedOption.optionValues.first { it.name == value.value }.id }
    }
}