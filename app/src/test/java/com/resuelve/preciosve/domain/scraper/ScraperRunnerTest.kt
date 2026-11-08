package com.resuelve.preciosve.domain.scraper

import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScraperRunnerTest {

    private fun item(id: String, total: Double) = ProductItem(
        id = id,
        store = Store.GAMA,
        name = id,
        basePriceRef = total,
        totalPriceRef = total,
        totalPriceVes = 0.0,
        imageUrl = "",
        productUrl = ""
    )

    @Test
    fun `exito devuelve los productos comparables y descarta los invalidos`() = runTest {
        val result = runScraper(Store.GAMA) {
            listOf(item("ok", 2.0), item("cero", 0.0), item("nan", Double.NaN))
        }
        assertTrue(result is ScraperResult.Success)
        result as ScraperResult.Success
        assertEquals(listOf("ok"), result.items.map { it.id })
        assertEquals(Store.GAMA, result.store)
    }

    @Test
    fun `una IOException se convierte en fallo de red`() = runTest {
        val result = runScraper(Store.FORUM) { throw IOException("sin red") }
        assertTrue(result is ScraperResult.Failure)
        result as ScraperResult.Failure
        assertTrue(result.error is ScraperError.Network)
        assertEquals("sin red", result.error.message)
    }

    @Test
    fun `un timeout de socket se clasifica como Timeout`() = runTest {
        val result = runScraper(Store.CENTRAL) { throw SocketTimeoutException("lento") }
        assertTrue((result as ScraperResult.Failure).error is ScraperError.Timeout)
    }

    @Test
    fun `un withTimeout interno se reporta como Timeout y no cancela al llamador`() = runTest {
        val result = runScraper(Store.GAMA) {
            withTimeout(100) { delay(10_000) }
            emptyList()
        }
        assertTrue((result as ScraperResult.Failure).error is ScraperError.Timeout)
    }

    @Test
    fun `la cancelacion se propaga`() = runTest {
        try {
            runScraper(Store.GAMA) { throw CancellationException("cancelado") }
            fail("Debió propagar CancellationException")
        } catch (e: CancellationException) {
            assertEquals("cancelado", e.message)
        }
    }

    @Test
    fun `una excepcion de parseo se clasifica como Parse`() = runTest {
        val result = runScraper(Store.GAMA) { throw ScraperParseException("html raro") }
        assertTrue((result as ScraperResult.Failure).error is ScraperError.Parse)
    }

    @Test
    fun `una excepcion inesperada se clasifica como Unknown y un argumento invalido como Parse`() = runTest {
        val unknown = runScraper(Store.GAMA) { throw IllegalStateException("boom") }
        assertTrue((unknown as ScraperResult.Failure).error is ScraperError.Unknown)
        val parse = runScraper(Store.GAMA) { throw IllegalArgumentException("x") }
        assertTrue((parse as ScraperResult.Failure).error is ScraperError.Parse)
    }
}
