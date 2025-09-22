package de.hinundhergestellt.jhuh.backend.shoptexter

import com.fasterxml.jackson.databind.json.JsonMapper
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ProductMapper
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.vectorstore.ExtendedVectorStore
import de.hinundhergestellt.jhuh.core.loadTextResource
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.prompt.PromptTemplate
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service


private val logger = KotlinLogging.logger { }

@Service
class ShopTexterService(
    private val categoryTexterService: CategoryTexterService,
    private val productTexterService: ProductTexterService,
    private val shopTexterChatClient: ChatClient,
    private val vectorStore: ExtendedVectorStore,
    shopifyDataStore: ShopifyDataStore,
    private val productMapper: ProductMapper,
    @param:Qualifier("shopTexterJsonMapper")
    private val jsonMapper: JsonMapper
) {
    private val productDetailsConverter = BeanOutputConverter(GeneratedProductDetails::class.java)
    private val productTagsConverter = BeanOutputConverter(GeneratedProductTags::class.java)

    private val examplesPromptTemplate = PromptTemplate(loadTextResource { "examples-prompt.txt" })

    init {
        updateProducts(shopifyDataStore.products)
    }

    fun updateProduct(product: ShopifyProduct) {
        updateProducts(listOf(product))
    }

    fun removeProduct(shopifyId: String) {
        vectorStore.delete("shopifyId == '$shopifyId'")
    }

    fun generateProductDetails(artooProduct: ArtooMappedProduct, syncProduct: SyncProduct, description: String?): GeneratedProductDetails {
        val product = productMapper.map(artooProduct, syncProduct, description)

        logger.info { "Generating product description for ${jsonMapper.writeValueAsString(product)}" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "product-details-prompt.txt" })
            .user {
                it.text("Produkt: {product}\n\n{format}")
                    .param("product", jsonMapper.writeValueAsString(product))
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
        val product = productMapper.map(artooProduct, syncProduct, description)

        logger.info { "Generating product tags for ${jsonMapper.writeValueAsString(product)}" }

        val callResponse = shopTexterChatClient.prompt()
            .system(loadTextResource { "product-tags-prompt.txt" })
            .user {
                it.text("Produkt: {product}\n\n{format}")
                    .param("product", jsonMapper.writeValueAsString(product))
                    .param("format", productTagsConverter.format)
            }
            .advisors { it.param(ChatMemory.CONVERSATION_ID, "productTags") }
            .call()
        val response = productTagsConverter.convert(callResponse.content()!!)!!

        logger.debug { "Generated tags: ${response.tags}" }
        logger.debug { "Consulted web sites: ${response.consultedUrls}" }

        return response
    }

    fun generateCategoryTexts(category: String, tags: Set<String>, products: List<ShopifyProduct>): CategoryTexts {
        val mappedProducts = products.map { productMapper.map(it) }
        val keywords = categoryTexterService.generateCategoryKeywords(category, tags, mappedProducts)
        val texts = categoryTexterService.generateCategoryTexts(category, tags, mappedProducts, keywords)
        val optimized = categoryTexterService.optimizeCategoryTexts(category, texts)
        return CategoryTexts(
            texts.seoTitle,
            texts.metaDescription,
            optimized.descriptionHtml
        )
    }

    fun generateProductTexts(product: ShopifyProduct): ProductTexts {
        val mappedProduct = productMapper.map(product)
        val keywords = productTexterService.generateProductKeywords(mappedProduct)
        val texts = productTexterService.generateProductTexts(mappedProduct, keywords)
        val optimized = productTexterService.optimizeProductTexts(mappedProduct, texts)
        return ProductTexts(
            texts.seoTitle,
            texts.metaDescription,
            optimized.descriptionHtml
        )
    }

    fun generateProductDetails(product: ShopifyProduct): ProductDetails {
        val mappedProduct = productMapper.map(product)
        return productTexterService.generateProductDetails(mappedProduct)
    }

    private fun updateProducts(products: List<ShopifyProduct>) {
        val newOrChangedDocuments = products.mapNotNull { product ->
            val oldText = vectorStore.find("shopifyId == '${product.id}'").firstOrNull()?.text
            val newText = jsonMapper.writeValueAsString(productMapper.map(product))
            if (oldText == null || oldText != newText) {
                println("DIFF")
                Document(newText, mapOf("shopifyId" to product.id))
            }
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

class CategoryTexts(
    val seoTitle: String,
    val metaDescription: String,
    val descriptionHtml: String
)

class ProductTexts(
    val seoTitle: String,
    val metaDescription: String,
    val descriptionHtml: String
)