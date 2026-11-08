package com.resuelve.preciosve.domain.scraper

import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store

/** Resultado de consultar una tienda. Nunca lanza: el fallo de una no debe tumbar a las demás. */
sealed interface ScraperResult {
    val store: Store

    /** Búsqueda exitosa (la lista puede estar vacía si no hay coincidencias). */
    data class Success(
        override val store: Store,
        val items: List<ProductItem>,
        val elapsedMs: Long
    ) : ScraperResult

    /** Búsqueda fallida con su causa clasificada. */
    data class Failure(
        override val store: Store,
        val error: ScraperError,
        val elapsedMs: Long = 0L
    ) : ScraperResult
}
