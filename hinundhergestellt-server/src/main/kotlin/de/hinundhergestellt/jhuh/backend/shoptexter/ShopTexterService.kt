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
    private val shopifyDataStore: ShopifyDataStore,
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
    private val categoryDescriptionConverterX = BeanOutputConverter(GeneratedCategoryDescription::class.java)
    private val keywordClustersConverter = BeanOutputConverter(KeywordClusters::class.java)
    private val categoryTextsConverter = BeanOutputConverter(CategoryTexts::class.java)
    private val categoryDescriptionConverter = BeanOutputConverter(CategoryDescription::class.java)

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
        val response = categoryDescriptionConverterX.convert(callResponse.content()!!)!!

        logger.debug { "Generated category description: ${response.description}" }

        return response
    }

    fun generateCategoryKeywords(category: String, tags: Set<String>): KeywordClusters {
        logger.info { "Generating category keywords for $category with tags $tags" }

        val examples = shopifyDataStore.products
            .filter { it.tags.intersect(tags).isNotEmpty() }
            .mapNotNull { product -> syncProductRepository.findByShopifyId(product.id)?.let { product to it } }
            .map { (shopify, sync) -> ProductMapper.map(shopify, sync) }
            .take(10)
        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "category-keywords-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "category-keywords-user-prompt.txt" })
                    .param("category", category)
                    .param("tags", tags)
                    .param("format", keywordClustersConverter.format)
                    .param("examples", examples.joinToString("\n") { product -> objectMapper.writeValueAsString(product) })
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "generateCategoryKeywords") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Generated category keyword clusters: $responseContent" }

        return keywordClustersConverter.convert(responseContent)!!
    }

    fun generateCategoryTexts(category: String, tags: Set<String>, keywords: KeywordClusters): CategoryTexts {
        logger.info { "Generating category description for $category with keywords cluster" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "category-texts-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "category-texts-user-prompt.txt" })
                    .param("category", category)
                    .param("tags", tags)
                    .param("keywords", objectMapper.writeValueAsString(keywords))
                    .param("format", categoryTextsConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "generateCategoryTexts") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Generated category texts: $responseContent" }

        return categoryTextsConverter.convert(responseContent)!!
    }

    fun optimizeCategoryTexts(category: String, texts: CategoryTexts): CategoryDescription {
        logger.info { "Optimizing category description for $category from previous prompt" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "category-optimize-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "category-optimize-user-prompt.txt" })
                    .param("category", category)
                    .param("texts", objectMapper.writeValueAsString(texts))
                    .param("format", categoryDescriptionConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "optimizeCategoryTexts") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Optimized category texts: $responseContent" }

        return categoryDescriptionConverter.convert(responseContent)!!
    }

    private fun updateProducts(products: List<Pair<ShopifyProduct, SyncProduct>>) {
        val newOrChangedDocuments = products.mapNotNull { (shopify, sync) ->
            val oldText = vectorStore.findById(sync.id)?.text
            val newText = objectMapper.writeValueAsString(ProductMapper.map(shopify, sync))
            if (oldText == null || oldText != newText) Document(sync.id.toString(), newText, mapOf())
            else null
        }
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

class KeywordClusters(
    val intentToKnow: List<String>,
    val intentToDo: List<String>,
    val intentToBuyOnline: List<String>,
    val intentToBuyLocal: List<String>,
    val multiIntent: List<String>
)

class CategoryTexts(
    val seoTitle: String,
    val metaDescription: String,
    val intro: String,
    val mainText: String,
    val callToAction: String
)

class CategoryDescription(
    val description: String
)