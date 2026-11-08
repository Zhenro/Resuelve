package com.resuelve.preciosve.domain.repository

import com.resuelve.preciosve.domain.model.ComparisonGroup
import kotlinx.coroutines.flow.Flow

/**
 * Contrato del repositorio de comparación de precios y catálogo maestro.
 */
interface PriceComparisonRepository {

    /**
     * Realiza una búsqueda progresiva y paralela en los supermercados con caché
     * instantánea local (Stale-While-Revalidate).
     */
    fun search(query: String): Flow<List<ComparisonGroup>>

    /**
     * Observa el historial de búsquedas recientes del usuario.
     */
    fun observeRecentSearches(): Flow<List<String>>

    /**
     * Elimina una búsqueda específica del historial reciente.
     */
    suspend fun deleteRecentSearch(query: String)

    /**
     * Limpia todo el historial de búsquedas recientes.
     */
    suspend fun clearRecentSearches()

    /**
     * Obtiene sugerencias de autocompletado en vivo basadas en marcas y catálogo local.
     */
    suspend fun getAutocompleteSuggestions(prefix: String): List<String>
}
