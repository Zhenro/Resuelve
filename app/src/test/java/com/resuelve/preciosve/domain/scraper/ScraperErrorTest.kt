package com.resuelve.preciosve.domain.scraper

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperErrorTest {

    @Test
    fun `codigos 401 403 y 429 son Blocked`() {
        for (code in listOf(401, 403, 429)) {
            val error = ScraperError.from(HttpStatusException(code, "x"))
            assertTrue("code $code", error is ScraperError.Blocked)
            assertEquals(code, (error as ScraperError.Blocked).httpCode)
        }
    }

    @Test
    fun `otros codigos HTTP son Network`() {
        assertTrue(ScraperError.from(HttpStatusException(500, "Server Error")) is ScraperError.Network)
        assertTrue(ScraperError.from(HttpStatusException(404, "Not Found")) is ScraperError.Network)
    }

    @Test
    fun `IOException generica es Network y conserva la causa`() {
        val cause = IOException("reset")
        val error = ScraperError.from(cause)
        assertTrue(error is ScraperError.Network)
        assertEquals("reset", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `excepcion sin mensaje usa el nombre de la clase`() {
        val error = ScraperError.from(UnsupportedOperationException())
        assertTrue(error is ScraperError.Unknown)
        assertEquals("UnsupportedOperationException", error.message)
    }

    @Test
    fun `HttpStatusException formatea el mensaje`() {
        assertEquals("HTTP 503 Service Unavailable", HttpStatusException(503, "Service Unavailable").message)
    }
}
