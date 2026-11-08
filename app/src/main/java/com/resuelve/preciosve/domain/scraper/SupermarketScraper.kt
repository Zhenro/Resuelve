package com.resuelve.preciosve.domain.scraper

import com.resuelve.preciosve.domain.model.Store

/**
 * Contrato común de los conectores de supermercados.
 *
 * Las implementaciones deben ser seguras para llamarse desde cualquier corrutina,
 * no bloquear el hilo principal y devolver siempre un [ScraperResult] (usar [runScraper]).
 */
interface SupermarketScraper {

    /** Tienda a la que pertenece este conector. */
    val store: Store

    /** Busca [query] en la tienda y devuelve productos con precios normalizados. */
    suspend fun search(query: String): ScraperResult
}
