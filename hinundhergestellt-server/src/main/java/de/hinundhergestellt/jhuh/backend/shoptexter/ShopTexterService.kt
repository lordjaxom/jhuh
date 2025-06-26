package de.hinundhergestellt.jhuh.backend.shoptexter

import com.fasterxml.jackson.databind.ObjectMapper
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ShopifyProductForAiMixin
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.vectorstore.findById
import de.hinundhergestellt.jhuh.util.loadTextResource
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.UnsavedShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger { }

@Service
class ShopTexterService(
    private val shopTexterChatClient: ChatClient,
    private val vectorStore: VectorStore,
    shopifyDataStore: ShopifyDataStore,
    syncProductRepository: SyncProductRepository,
) {
    private val objectMapper = ObjectMapper()
        .addMixIn(ShopifyProduct::class.java, ShopifyProductForAiMixin::class.java)

    private val outputConverter = BeanOutputConverter(ShopTexterResponse::class.java)

    private val examplesPromptTemplate = PromptTemplate(loadTextResource { "examples-prompt.txt" })

    init {
        val newOrChangedDocuments = shopifyDataStore.products.asSequence()
            .mapNotNull { syncProductRepository.findByShopifyId(it.id)?.run { id to it } }
            .map { (id, product) -> id to objectMapper.writeValueAsString(product) }
            .filter { (id, text) -> vectorStore.findById(id)?.let { it.text != text } ?: true }
            .map { (id, text) -> Document(id.toString(), text, mapOf<String, Any>()) }
            .toList()
        logger.info { "Adding or updating ${newOrChangedDocuments.size} products in vector store" }
        vectorStore.add(newOrChangedDocuments)
    }

    suspend fun generate(product: UnsavedShopifyProduct): ShopTexterResponse {
        logger.info { "Generating product description for $product" }

        val content = shopTexterChatClient.prompt()
            .user {
                it.text("Produkt: {product}\n\n{format}")
                    .param("product", objectMapper.writeValueAsString(product))
                    .param("format", outputConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "shopTexter") }
            .advisors(
                QuestionAnswerAdvisor
                    .builder(vectorStore)
                    .promptTemplate(examplesPromptTemplate)
                    .searchRequest(
                        SearchRequest.builder()
                            .query(product.title)
                            .topK(3)
                            .build()
                    )
                    .build()
            )
            .stream()
            .content()
            .collectList()
            .awaitSingle()
            .joinToString("")
        val response = outputConverter.convert(content)!!

        logger.debug { "Generated product description: ${response.description}" }
        logger.debug { "Generated techical details: ${response.technicalDetails}" }
        logger.debug { "Consulted web sites: ${response.consultedUrls}" }

        return response
    }
}

class ShopTexterResponse(
    val description: String,
    val technicalDetails: String,
    val consultedUrls: List<String>
)