package com.resuelve.preciosve.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tabla que almacena cada consulta de búsqueda realizada, su timestamp de caducidad (TTL 10 min)
 * y metadatos.
 */
@Entity(
    tableName = "search_queries",
    indices = [Index(value = ["query"], unique = true)]
)
data class SearchQueryEntity(
    @PrimaryKey val query: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long
)
