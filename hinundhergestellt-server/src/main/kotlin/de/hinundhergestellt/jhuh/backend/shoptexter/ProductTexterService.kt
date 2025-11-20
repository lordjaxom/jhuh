package de.hinundhergestellt.jhuh.backend.shoptexter

import com.fasterxml.jackson.databind.json.JsonMapper
import de.hinundhergestellt.jhuh.backend.shoptexter.model.Product
import de.hinundhergestellt.jhuh.core.loadTextResource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ProductTexterService(
    private val shopTexterChatClient: ChatClient,
    @param:Qualifier("shopTexterJsonMapper")
    private val jsonMapper: JsonMapper
) {
    private val keywordClustersConverter = BeanOutputConverter(ProductKeywordClusters::class.java)
    private val rawTextsConverter = BeanOutputConverter(ProductRawTexts::class.java)
    private val descriptionConverter = BeanOutputConverter(ProductDescription::class.java)
    private val detailsConverter = BeanOutputConverter(ProductDetails::class.java)
    private val reworkConverter = BeanOutputConverter(ProductRework::class.java)

    fun generateProductKeywords(product: Product): ProductKeywordClusters {
        logger.info { "Generating product keywords for ${product.title}" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "product-keywords-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "product-keywords-user-prompt.txt" })
                    .param("product", jsonMapper.writeValueAsString(product))
                    .param("format", keywordClustersConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "generateProductKeywords") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Generated product keyword clusters: $responseContent" }

        return keywordClustersConverter.convert(responseContent)!!
    }

    fun generateProductTexts(product: Product, keywords: ProductKeywordClusters): ProductRawTexts {
        logger.info { "Generating product description for ${product.title} with keyword cluster" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "product-texts-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "product-texts-user-prompt.txt" })
                    .param("product", jsonMapper.writeValueAsString(product))
                    .param("keywords", jsonMapper.writeValueAsString(keywords))
                    .param("format", rawTextsConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "generateProductTexts") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Generated product texts: $responseContent" }

        return rawTextsConverter.convert(responseContent)!!
    }

    fun optimizeProductTexts(product: Product, texts: ProductRawTexts): ProductDescription {
        logger.info { "Optimizing product description for ${product.title} from previous prompt" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "product-optimize-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "product-optimize-user-prompt.txt" })
                    .param("product", jsonMapper.writeValueAsString(product))
                    .param("texts", jsonMapper.writeValueAsString(texts))
                    .param("format", descriptionConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "optimizeProductTexts") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Optimized product texts: $responseContent" }

        return descriptionConverter.convert(responseContent)!!
    }

    fun generateProductDetails(product: Product): ProductDetails {
        logger.info { "Generating product details for ${product.title}" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "product-details-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "product-details-user-prompt.txt" })
                    .param("product", jsonMapper.writeValueAsString(product))
                    .param("format", detailsConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "generateProductDetails") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Generated product details: $responseContent" }

        return detailsConverter.convert(responseContent)!!
    }

    fun reworkProductTexts(product: Product): ProductRework {
        logger.info { "Reworking product texts for ${product.title}" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "product-rework-system-prompt.txt" })
            .user {
                it.text(loadTextResource { "product-rework-user-prompt.txt" })
                    .param("product", jsonMapper.writeValueAsString(product))
                    .param("format", reworkConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "reworkProductTexts") }
            .call()
        val responseContent = callResponse.content()!!

        logger.info { "Reworked product texts: $responseContent" }

        return reworkConverter.convert(responseContent)!!
    }
}

class ProductKeywordClusters(
    val intentToKnow: List<String>,
    val intentToDo: List<String>,
    val intentToBuyOnline: List<String>,
    val intentToBuyLocal: List<String>,
    val multiIntent: List<String>
)

class ProductRawTexts(
    val seoTitle: String,
    val metaDescription: String,
    val intro: String,
    val mainText: String,
    val callToAction: String,
)

class ProductDescription(
    val descriptionHtml: String
)

class ProductDetails(
    val productType: String,
    val tags: List<String>,
    val technicalDetails: Map<String, String>
)

class ProductRework(
    var handle: String,
    var title: String,
    var seoTitle: String,
    var seoDescription: String,
    var descriptionHtml: String
)