package de.hinundhergestellt.jhuh.backend.shoptexter

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.addMixIn
import com.fasterxml.jackson.module.kotlin.kotlinModule
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ProductMapper
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ShopifyMetafieldForAiMixin
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ShopifyProductForAiMixin
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ShopifyProductOptionForAiMixin
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProductRepository
import de.hinundhergestellt.jhuh.backend.vectorstore.ExtendedVectorStore
import de.hinundhergestellt.jhuh.backend.vectorstore.findById
import de.hinundhergestellt.jhuh.core.loadTextResource
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyMetafield
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProductOption
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
        .addModule(kotlinModule())
        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
        .addMixIn<ShopifyProduct, ShopifyProductForAiMixin>()
        .addMixIn<ShopifyProductOption, ShopifyProductOptionForAiMixin>()
        .addMixIn<ShopifyMetafield, ShopifyMetafieldForAiMixin>()
        .build()

    private val productDetailsConverter = BeanOutputConverter(GeneratedProductDetails::class.java)
    private val productTagsConverter = BeanOutputConverter(GeneratedProductTags::class.java)
    private val categoryDescriptionConverter = BeanOutputConverter(GeneratedCategoryDescription::class.java)

    private val examplesPromptTemplate = PromptTemplate(loadTextResource { "examples-prompt.txt" })

    init {
        val productsWithSync = shopifyDataStore.products
            .mapNotNull { product -> syncProductRepository.findByShopifyId(product.id)?.let { product to it } }
        updateProducts(productsWithSync)
    }

    fun updateProduct(shopify: ShopifyProduct, sync: SyncProduct) {
        updateProducts(listOf(Pair(shopify, sync)))
    }

    fun updateProduct(product: ShopifyProduct) {
        updateProduct(product, syncProductRepository.findByShopifyId(product.id) ?: return)
    }

    fun removeProduct(id: UUID) {
        vectorStore.removeById(id.toString())
    }

    fun generateProductDetails(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct, description: String?): GeneratedProductDetails {
        val product = ProductMapper.map(artooProduct, syncProduct, description)

        logger.info { "Generating product description for ${objectMapper.writeValueAsString(product)}" }

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
                            .query("Produkte ähnlich ${product.name}")
                            .topK(3)
                            .build()
                    )
                    .build()
            )
            .call()
        val response = productDetailsConverter.convert(callResponse.content()!!)!!

        logger.debug { "Generated product description: ${response.descriptionHtml}" }
        logger.debug { "Generated techical details: ${response.technicalDetails}" }
        logger.debug { "Generated tags: ${response.tags}" }
        logger.debug { "Consulted web sites: ${response.consultedUrls}" }

        return response
    }

    fun generateProductTags(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct, description: String?): GeneratedProductTags {
        val product = ProductMapper.map(artooProduct, syncProduct, description)

        logger.info { "Generating product tags for ${objectMapper.writeValueAsString(product)}" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "product-tags-prompt.txt" })
            .user {
                it.text("Produkt: {product}\n\n{format}")
                    .param("product", objectMapper.writeValueAsString(product))
                    .param("format", productTagsConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "productTags") }
            .call()
        val response = productTagsConverter.convert(callResponse.content()!!)!!

        logger.debug { "Generated tags: ${response.tags}" }
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

    private fun updateProducts(products: List<Pair<ShopifyProduct, SyncProduct>>) {
        val newOrChangedDocuments = products.asSequence()
            .map { (shopify, sync) -> Pair(sync.id, ProductMapper.map(shopify, sync)) }
            .map { (id, product) -> Triple(id, objectMapper.writeValueAsString(product), vectorStore.findById(id)?.text) }
            .filter { (_, newText, oldText) -> oldText == null || oldText != newText }
            .map { (id, text, _) -> Document(id.toString(), text, mapOf<String, Any>()) }
            .toList()
        logger.info { "Updating ${newOrChangedDocuments.size} products in vector store" }
        vectorStore.add(newOrChangedDocuments)
    }
}

class GeneratedProductDetails(
    val descriptionHtml: String,
    val technicalDetails: Map<String, String>,
    val tags: List<String>,
    val productType: String,
    val consultedUrls: List<String>
)

class GeneratedProductTags(
    val tags: List<String>,
    val consultedUrls: List<String>
)

class GeneratedCategoryDescription(
    val description: String
)