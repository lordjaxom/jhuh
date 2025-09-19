package de.hinundhergestellt.jhuh.backend.vectorstore

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore
import org.springframework.ai.vectorstore.mariadb.autoconfigure.MariaDbStoreProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

interface ExtendedVectorStore : VectorStore {

    fun find(filterExpression: String): List<Document>
}

@Component
@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
class MariaDBExtendedVectorStore(
    private val vectorStore: MariaDBVectorStore,
    private val properties: MariaDbStoreProperties
) : ExtendedVectorStore, VectorStore by vectorStore {

    private val jdbcTemplate = vectorStore.getNativeClient<JdbcTemplate>().get()
    private val fullyQualifiedTableName = sequenceOf(properties.schemaName, properties.tableName).filterNotNull().joinToString(".")
    private val idFieldName: String by properties::idFieldName
    private val contentFieldName: String by properties::contentFieldName

    override fun find(filterExpression: String): List<Document> {
        val nativeFilterExpression = vectorStore.filterExpressionConverter
            .convertExpression(FilterExpressionTextParser().parse(filterExpression));
        return jdbcTemplate.query(
            "select $idFieldName, $contentFieldName from $fullyQualifiedTableName where $nativeFilterExpression",
            { rs, _ -> Document(rs.getString(1), rs.getString(2), mapOf()) },
        )
    }
}
