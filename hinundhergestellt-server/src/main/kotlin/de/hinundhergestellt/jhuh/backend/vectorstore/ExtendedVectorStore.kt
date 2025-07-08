package de.hinundhergestellt.jhuh.backend.vectorstore

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore
import org.springframework.ai.vectorstore.mariadb.autoconfigure.MariaDbStoreProperties
import org.springframework.dao.support.DataAccessUtils.singleResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

interface ExtendedVectorStore : VectorStore {

    fun findById(id: String): Document?

    fun removeById(id: String)
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
    private val contentFieldname: String by properties::contentFieldName

    override fun findById(id: String) =
        singleResult(
            jdbcTemplate.query(
                "select $idFieldName, $contentFieldname from $fullyQualifiedTableName where id=?",
                { rs, rowNum -> Document(rs.getString(1), rs.getString(2), mapOf()) },
                id
            )
        )

    override fun removeById(id: String) {
        jdbcTemplate.update("delete from $fullyQualifiedTableName where id=?", id)
    }
}

fun ExtendedVectorStore.findById(id: UUID) = when (this) {
    is MariaDBExtendedVectorStore -> findById(id.toString())
    else -> throw UnsupportedOperationException("findById")
}