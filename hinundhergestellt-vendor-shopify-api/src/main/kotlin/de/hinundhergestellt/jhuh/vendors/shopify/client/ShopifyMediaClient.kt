package de.hinundhergestellt.jhuh.vendors.shopify.client

import com.netflix.graphql.dgs.client.WebClientGraphQLClient
import de.hinundhergestellt.jhuh.HuhProperties
import de.hinundhergestellt.jhuh.core.mapParallel
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildMutation
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.DgsClient.buildQuery
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.File
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileConnection
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileContentType
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileCreateInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileCreateInputDuplicateResolutionMode
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileCreatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileDeletePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileEdge
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileStatus
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.FileUpdatePayload
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.PageInfo
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.StagedMediaUploadTarget
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.StagedUploadHttpMethodType
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.StagedUploadInput
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.StagedUploadTargetGenerateUploadResource
import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.StagedUploadsCreatePayload
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.fileSize

private val logger = KotlinLogging.logger {}

@Component
class ShopifyMediaClient(
    private val shopifyGraphQLClient: WebClientGraphQLClient,
    private val genericWebClient: WebClient,
    private val properties: HuhProperties
) {
    fun fetchAll(query: String? = null) = pageAll { fetchNextPage(it, query) }.map { it.toShopifyMedia() }

    suspend fun upload(files: List<Path>) = coroutineScope {
        val stagedTargets = createStagedUploads(files)

        files.zip(stagedTargets).mapParallel(properties.processingThreads) { (file, staged) -> uploadToStaging(file, staged) }

        val createdFiles = createFiles(stagedTargets)
        val processedFiles = waitForFiles(createdFiles)
        processedFiles.map { ShopifyMedia(it) }
    }

    suspend fun update(medias: List<ShopifyMedia>, referencesToAdd: List<String>? = null, referencesToRemove: List<String>? = null) {
        val request = buildMutation {
            fileUpdate(medias.map { it.toFileUpdateInput(referencesToAdd, referencesToRemove) }) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, FileUpdatePayload::userErrors)
    }

    suspend fun delete(medias: List<ShopifyMedia>) {
        val request = buildMutation {
            fileDelete(medias.map { it.id }) {
                userErrors { message; field }
            }
        }

        shopifyGraphQLClient.executeMutation(request, FileDeletePayload::userErrors)
    }

    private suspend fun fetchNextPage(after: String?, query: String?): Pair<List<FileEdge>, PageInfo> {
        val request = buildQuery {
            files(first = 250, after = after, query = query) {
                edges {
                    node {
                        onMediaImage {
                            id
                            image { id; src; altText }
                        }
                    }
                }
                pageInfo { hasNextPage; endCursor }
            }
        }

        val payload = shopifyGraphQLClient.executeQuery<FileConnection>(request)
        return Pair(payload.edges, payload.pageInfo)
    }


    private suspend fun createStagedUploads(files: List<Path>): List<StagedMediaUploadTarget> {
        val request = buildMutation {
            stagedUploadsCreate(files.map { it.toStagedUploadInput() }) {
                stagedTargets {
                    url; resourceUrl
                    parameters { name; value }
                }
                userErrors { message; field }
            }
        }

        val payload = shopifyGraphQLClient.executeMutation(request, StagedUploadsCreatePayload::userErrors)
        return payload.stagedTargets!!
    }

    private suspend fun createFiles(stagedTargets: List<StagedMediaUploadTarget>): List<File> {
        val request = buildMutation {
            fileCreate(stagedTargets.map { it.toFileCreateInput() }) {
                files {
                    fileStatus
                    onMediaImage {
                        id
                        image { id; src; altText }
                    }
                }
                userErrors { message; field }
            }
        }

        val payload = shopifyGraphQLClient.executeMutation(request, FileCreatePayload::userErrors)
        return payload.files!!
    }

    private suspend fun waitForFiles(files: List<File>) = buildList {
        val unprocessed = files.asSequence().associateBy { it.id }.toMutableMap()
        while (unprocessed.isNotEmpty()) {
            logger.info { "Still ${unprocessed.size} files unprocessed, delaying..." }

            delay(1_000)

            val query = unprocessed.keys.joinToString(" OR ") { "(id:${it.substringAfterLast("/")})" }
            val request = buildQuery {
                files(first = unprocessed.size, query = query) {
                    edges {
                        node {
                            fileStatus
                            onMediaImage {
                                id
                                image { id; src; altText }
                            }
                        }
                    }
                }
            }

            val payload = shopifyGraphQLClient.executeQuery<FileConnection>(request)
            payload.edges.asSequence()
                .map { it.node }
                .filter { it.fileStatus in arrayOf(FileStatus.READY, FileStatus.FAILED) }
                .forEach { unprocessed.remove(it.id); add(it) }
        }
    }

    private suspend fun uploadToStaging(file: Path, stagedTarget: StagedMediaUploadTarget) {
        logger.info { "Uploading $file to staging" }

        val bodyBuilder = MultipartBodyBuilder()
        stagedTarget.parameters.forEach { bodyBuilder.part(it.name, it.value) }
        bodyBuilder.part("file", FileSystemResource(file))
        genericWebClient.post()
            .uri(stagedTarget.url!!)
            .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
            .exchangeToMono {
                if (it.statusCode() == HttpStatus.CREATED) Mono.empty<Void>()
                else Mono.error(RuntimeException("Status " + it.statusCode())) // TODO
            }
            .awaitFirstOrNull()
    }
}

private fun FileEdge.toShopifyMedia() =
    ShopifyMedia(node)

private fun Path.toStagedUploadInput() =
    StagedUploadInput(
        resource = StagedUploadTargetGenerateUploadResource.IMAGE,
        filename = fileName.toString(),
        mimeType = toMimeType(),
        httpMethod = StagedUploadHttpMethodType.POST,
        fileSize = fileSize().toString(),
    )

private fun Path.toMimeType() =
    when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> throw IllegalArgumentException(extension)
    }

private fun StagedMediaUploadTarget.toFileCreateInput() =
    FileCreateInput(
        contentType = FileContentType.IMAGE,
        duplicateResolutionMode = FileCreateInputDuplicateResolutionMode.RAISE_ERROR,
        originalSource = resourceUrl!!
    )