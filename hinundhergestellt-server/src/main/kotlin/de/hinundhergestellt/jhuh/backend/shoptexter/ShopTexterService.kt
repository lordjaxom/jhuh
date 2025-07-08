package de.hinundhergestellt.jhuh.backend.shoptexter

import com.fasterxml.jackson.databind.ObjectMapper
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ShopifyProductForAiMixin
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.vectorstore.ExtendedVectorStore
import de.hinundhergestellt.jhuh.backend.vectorstore.findById
import de.hinundhergestellt.jhuh.core.loadTextResource
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
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
    private val objectMapper = ObjectMapper()
        .addMixIn(ShopifyProduct::class.java, ShopifyProductForAiMixin::class.java)

    private val outputConverter = BeanOutputConverter(ShopTexterResponse::class.java)

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

    fun generate(product: UnsavedShopifyProduct): ShopTexterResponse {
        logger.info { "Generating product description for $product" }

        val callResponse = shopTexterChatClient.prompt()
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
            .call()
        val response = outputConverter.convert(callResponse.content()!!)!!

        logger.debug { "Generated product description: ${response.description}" }
        logger.debug { "Generated techical details: ${response.technicalDetails}" }
        logger.debug { "Consulted web sites: ${response.consultedUrls}" }

        return response
    }

    fun updateProducts(products: List<Pair<UUID, ShopifyProduct>>) {
        val newOrChangedDocuments = products.asSequence()
            .map { (id, product) -> id to objectMapper.writeValueAsString(product) }
            .filter { (id, text) -> vectorStore.findById(id)?.let { it.text != text } ?: true }
            .map { (id, text) -> Document(id.toString(), text, mapOf<String, Any>()) }
            .toList()
        logger.info { "Updating ${newOrChangedDocuments.size} products in vector store" }
        vectorStore.add(newOrChangedDocuments)
    }
}

class ShopTexterResponse(
    val description: String,
    val technicalDetails: String,
    val consultedUrls: List<String>
)