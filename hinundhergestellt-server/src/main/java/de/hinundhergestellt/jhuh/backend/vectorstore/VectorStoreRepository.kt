package de.hinundhergestellt.jhuh.backend.vectorstore

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore
import org.springframework.ai.vectorstore.mariadb.autoconfigure.MariaDbStoreProperties
import org.springframework.dao.support.DataAccessUtils.singleResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class MariaDBVectorStoreRepository(
    private val properties: MariaDbStoreProperties
) {
    private val idFieldName: String by properties::idFieldName
    private val contentFieldname: String by properties::contentFieldName
    private val fullyQualifiedTableName = sequenceOf(properties.schemaName, properties.tableName).filterNotNull().joinToString(".")

    init {
        repository = this
    }

    fun findById(jdbcTemplate: JdbcTemplate, id: UUID) =
        singleResult(
            jdbcTemplate.query(
                "select $idFieldName, $contentFieldname from $fullyQualifiedTableName where id=?",
                { rs, rowNum -> Document(rs.getString(1), rs.getString(2), mapOf()) },
                id
            )
        )
}

private lateinit var repository: MariaDBVectorStoreRepository

fun VectorStore.findById(id: UUID) = when (this) {
    is MariaDBVectorStore -> findById(id)
    else -> throw UnsupportedOperationException("findById")
}

private fun MariaDBVectorStore.findById(id: UUID) = repository.findById(jdbcTemplate, id)

private val MariaDBVectorStore.jdbcTemplate get() = getNativeClient<JdbcTemplate>().get()