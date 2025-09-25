package de.hinundhergestellt.jhuh.backend.shoptexter

import com.fasterxml.jackson.databind.json.JsonMapper
import de.hinundhergestellt.jhuh.backend.shoptexter.model.Product
import de.hinundhergestellt.jhuh.backend.shoptexter.model.ProductMapper
import de.hinundhergestellt.jhuh.backend.syncdb.SyncProduct
import de.hinundhergestellt.jhuh.backend.vectorstore.ExtendedVectorStore
import de.hinundhergestellt.jhuh.vendors.ready2order.datastore.ArtooMappedProduct
import de.hinundhergestellt.jhuh.vendors.shopify.client.ShopifyProduct
import de.hinundhergestellt.jhuh.vendors.shopify.datastore.ShopifyDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.ai.document.Document
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger { }

@Service
class ShopTexterService(
    private val categoryTexterService: CategoryTexterService,
    private val productTexterService: ProductTexterService,
    private val vectorStore: ExtendedVectorStore,
    shopifyDataStore: ShopifyDataStore,
    private val productMapper: ProductMapper,
    @param:Qualifier("shopTexterJsonMapper")
    private val jsonMapper: JsonMapper
) {
    init {
        updateProducts(shopifyDataStore.products)
    }

    fun updateProduct(product: ShopifyProduct) {
        updateProducts(listOf(product))
    }

    fun updateProducts(products: Collection<ShopifyProduct>) {
        val outdatedDocumentIds = mutableListOf<String>()
        val newOrChangedDocuments = mutableListOf<Document>()
        products.forEach { product ->
            val document = vectorStore.find("shopifyId == '${product.id}'").firstOrNull()
            val newText = jsonMapper.writeValueAsString(productMapper.map(product))
            if (document === null || document.text!! != newText) {
                if (document !== null) outdatedDocumentIds += document.id
                newOrChangedDocuments += Document(newText, mapOf("shopifyId" to product.id))
            }
        }
        logger.info { "Updating ${newOrChangedDocuments.size} products in vector store" }
        vectorStore.delete(outdatedDocumentIds)
        vectorStore.add(newOrChangedDocuments)
    }

    fun removeProduct(shopifyId: String) {
        vectorStore.delete("shopifyId == '$shopifyId'")
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

    fun generateProductTexts(artoo: ArtooMappedProduct, sync: SyncProduct, description: String?) =
        generateProductTexts(productMapper.map(artoo, sync, description))

    fun generateProductTexts(product: ShopifyProduct) =
        generateProductTexts(productMapper.map(product))

    private fun generateProductTexts(product: Product): ProductTexts {
        val keywords = productTexterService.generateProductKeywords(product)
        val texts = productTexterService.generateProductTexts(product, keywords)
        val optimized = productTexterService.optimizeProductTexts(product, texts)
        return ProductTexts(
            texts.seoTitle,
            texts.metaDescription,
            optimized.descriptionHtml
        )
    }

    fun generateProductDetails(artoo: ArtooMappedProduct, sync: SyncProduct, description: String?) =
        generateProductDetails(productMapper.map(artoo, sync, description))

    fun generateProductDetails(product: ShopifyProduct) =
        generateProductDetails(productMapper.map(product))

    private fun generateProductDetails(product: Product) =
        productTexterService.generateProductDetails(product)
}

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