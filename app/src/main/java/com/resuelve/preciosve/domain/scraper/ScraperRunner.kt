package com.resuelve.preciosve.domain.scraper

import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Ejecuta [block] midiendo su duración y lo convierte en un [ScraperResult] sin lanzar.
 *
 * - La cancelación real de la corrutina padre se propaga (no se traga).
 * - Un `withTimeout` interno que expira se reporta como [ScraperError.Timeout].
 * - Productos sin precio utilizable ([ProductItem.isComparable]) se descartan.
 */
suspend inline fun runScraper(
    store: Store,
    crossinline block: suspend () -> List<ProductItem>
): ScraperResult {
    val startNanos = System.nanoTime()
    return try {
        val items = block().filter { it.isComparable }
        ScraperResult.Success(store, items, (System.nanoTime() - startNanos) / 1_000_000L)
    } catch (e: TimeoutCancellationException) {
        // Si la corrutina padre fue cancelada, ensureActive relanza; si no, fue un timeout interno.
        currentCoroutineContext().ensureActive()
        ScraperResult.Failure(
            store,
            ScraperError.from(e),
            (System.nanoTime() - startNanos) / 1_000_000L
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ScraperResult.Failure(
            store,
            ScraperError.from(e),
            (System.nanoTime() - startNanos) / 1_000_000L
        )
    }
}
