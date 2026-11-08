package com.resuelve.preciosve.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room para persistir el historial de búsquedas recientes del usuario.
 */
@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val timestamp: Long
)
