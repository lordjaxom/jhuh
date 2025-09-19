package de.hinundhergestellt.jhuh.backend.shoptexter

import com.fasterxml.jackson.databind.json.JsonMapper
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ProductMapper
import de.hinundhergestellt.jhuh.core.loadTextResource
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class CategoryTexterService(
    private val shopTexterChatClient: ChatClient,
    private val productMapper: ProductMapper,
    @param:Qualifier("shopTexterJsonMapper")
    private val jsonMapper: JsonMapper,
) {
    private val keywordClustersConverter = BeanOutputConverter(KeywordClusters::class.java)
    private val rawCategoryTextsConverter = BeanOutputConverter(RawCategoryTexts::class.java)
    private val categoryDescriptionConverter = BeanOutputConverter(CategoryDescription::class.java)

    fun generateCategoryKeywords(category: String, tags: Set<String>, products: List<ShopifyProduct>): KeywordClusters {
        logger.info { "Generating category keywords for $category with tags $tags" }

        val mappedProducts = products.map { productMapper.map(it) }
        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "category-keywords-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "category-keywords-user-prompt.txt" })
                    .param("category", category)
                    .param("tags", tags.joinToString(", "))
                    .param("format", keywordClustersConverter.format)
                    .param("products", mappedProducts.joinToString("\n") { product -> jsonMapper.writeValueAsString(product) })
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "generateCategoryKeywords") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Generated category keyword clusters: $responseContent" }

        return keywordClustersConverter.convert(responseContent)!!
    }

    fun generateCategoryTexts(
        category: String,
        tags: Set<String>,
        products: List<ShopifyProduct>,
        keywords: KeywordClusters
    ): RawCategoryTexts {
        logger.info { "Generating category description for $category with keywords cluster" }

        val mappedProducts = products.map { productMapper.map(it) }
        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "category-texts-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "category-texts-user-prompt.txt" })
                    .param("category", category)
                    .param("tags", tags.joinToString(", "))
                    .param("keywords", jsonMapper.writeValueAsString(keywords))
                    .param("products", mappedProducts.joinToString("\n") { product -> jsonMapper.writeValueAsString(product) })
                    .param("format", rawCategoryTextsConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "generateCategoryTexts") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Generated category texts: $responseContent" }

        return rawCategoryTextsConverter.convert(responseContent)!!
    }

    fun optimizeCategoryTexts(category: String, texts: RawCategoryTexts): CategoryDescription {
        logger.info { "Optimizing category description for $category from previous prompt" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "category-optimize-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "category-optimize-user-prompt.txt" })
                    .param("category", category)
                    .param("texts", jsonMapper.writeValueAsString(texts))
                    .param("format", categoryDescriptionConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "optimizeCategoryTexts") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Optimized category texts: $responseContent" }

        return categoryDescriptionConverter.convert(responseContent)!!
    }
}

class KeywordClusters(
    val intentToKnow: List<String>,
    val intentToDo: List<String>,
    val intentToBuyOnline: List<String>,
    val intentToBuyLocal: List<String>,
    val multiIntent: List<String>
)

class RawCategoryTexts(
    val seoTitle: String,
    val metaDescription: String,
    val intro: String,
    val mainText: String,
    val callToAction: String
)

class CategoryDescription(
    val descriptionHtml: String
)