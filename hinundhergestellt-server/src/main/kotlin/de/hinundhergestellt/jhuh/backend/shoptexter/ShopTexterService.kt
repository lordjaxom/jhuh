package de.hinundhergestellt.jhuh.backend.shoptexter

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.addMixIn
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ShopifyMetafieldForAiMixin
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ShopifyProductForAiMixin
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ShopifyProductOptionForAiMixin
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.vectorstore.ExtendedVectorStore
import de.hinundhergestellt.jhuh.backend.vectorstore.findById
import de.hinundhergestellt.jhuh.core.loadTextResource
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOption
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger { }

@Service
class ShopTexterService(
    private val shopTexterChatClient: ChatClient,
    private val vectorStore: ExtendedVectorStore,
    shopifyDataStore: ShopifyDataStore,
    private val syncProductRepository: SyncProductRepository,
) {
    private val objectMapper = JsonMapper.builder()
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
        .addMixIn<ShopifyProduct, ShopifyProductForAiMixin>()
        .addMixIn<ShopifyProductOption, ShopifyProductOptionForAiMixin>()
        .addMixIn<ShopifyMetafield, ShopifyMetafieldForAiMixin>()
        .build()

    private val productDetailsConverter = BeanOutputConverter(GeneratedProductDetails::class.java)
    private val categoryDescriptionConverter = BeanOutputConverter(GeneratedCategoryDescription::class.java)

    private val examplesPromptTemplate = PromptTemplate(loadTextResource { "examples-prompt.txt" })

    init {
        val productsWithUUID = shopifyDataStore.products
            .mapNotNull { syncProductRepository.findByShopifyId(it.id)?.run { id to it } }
        updateProducts(productsWithUUID)
    }

    fun updateProduct(id: UUID, product: ShopifyProduct) {
        updateProducts(listOf(Pair(id, product)))
    }

    fun removeProduct(id: UUID) {
        vectorStore.removeById(id.toString())
    }

    fun generateProductDetails(product: UnsavedShopifyProduct): GeneratedProductDetails {
        logger.info { "Generating product description for $product" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "product-details-prompt.txt" })
            .user {
                it.text("Produkt: {product}\n\n{format}")
                    .param("product", objectMapper.writeValueAsString(product))
                    .param("format", productDetailsConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "productDetails") }
            .advisors(
                QuestionAnswerAdvisor
                    .builder(vectorStore)
                    .promptTemplate(examplesPromptTemplate)
                    .searchRequest(
                        SearchRequest.builder()
                            .query("Produkte ähnlich ${product.title}")
                            .topK(3)
                            .build()
                    )
                    .build()
            )
            .call()
        val response = productDetailsConverter.convert(callResponse.content()!!)!!

        logger.debug { "Generated product description: ${response.description}" }
        logger.debug { "Generated techical details: ${response.technicalDetails}" }
        logger.debug { "Consulted web sites: ${response.consultedUrls}" }

        return response
    }

    fun generateCategoryDescription(name: String, tags: Set<String>): GeneratedCategoryDescription {
        logger.info { "Generating category description for $tags" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "category-description-prompt.txt" })
            .user {
                it.text("Kategorie: {name}\nTags: $tags\n\n{format}")
                    .param("name", name)
                    .param("tags", tags.joinToString(", "))
                    .param("format", productDetailsConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "categoryDescription") }
            .advisors(
                QuestionAnswerAdvisor
                    .builder(vectorStore)
                    .promptTemplate(examplesPromptTemplate)
                    .searchRequest(
                        SearchRequest.builder()
                            .query("Tags ${tags.joinToString(", ")}")
                            .topK(3)
                            .build()
                    )
                    .build()
            )
            .call()
        val response = categoryDescriptionConverter.convert(callResponse.content()!!)!!

        logger.debug { "Generated category description: ${response.description}" }

        return response
    }

    fun updateProducts(products: List<Pair<UUID, ShopifyProduct>>) {
        val newOrChangedDocuments = products.asSequence()
            .map { (id, product) -> Triple(id, objectMapper.writeValueAsString(product), vectorStore.findById(id)?.text) }
            .filter { (_, newText, oldText) -> oldText == null || oldText != newText }
            .map { (id, text, _) -> Document(id.toString(), text, mapOf<String, Any>()) }
            .toList()
        logger.info { "Updating ${newOrChangedDocuments.size} products in vector store" }
        vectorStore.add(newOrChangedDocuments)
    }
}

class GeneratedProductDetails(
    val description: String,
    val technicalDetails: String,
    val consultedUrls: List<String>
)

class GeneratedCategoryDescription(
    val description: String
)