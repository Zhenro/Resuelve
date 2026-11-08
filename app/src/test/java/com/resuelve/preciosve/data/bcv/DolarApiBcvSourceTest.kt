package com.resuelve.preciosve.data.bcv

import com.resuelve.preciosve.domain.scraper.HttpStatusException
import com.resuelve.preciosve.domain.scraper.ScraperParseException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DolarApiBcvSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var source: DolarApiBcvSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        source = DolarApiBcvSource(OkHttpClient(), server.url("/v1/dolares/oficial").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `lee el promedio de la respuesta real de la API`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"moneda":"USD","fuente":"oficial","nombre":"Dólar","compra":null,"venta":null,""" +
                    """"promedio":880.1159,"fechaActualizacion":"2026-10-16T00:00:00-04:00"}"""
            )
        )
        assertEquals(880.1159, source.fetchVesPerRef(), 1e-9)
        assertEquals("application/json", server.takeRequest().getHeader("Accept"))
    }

    @Test
    fun `usa venta si el promedio no viene`() = runTest {
        server.enqueue(MockResponse().setBody("""{"promedio":null,"venta":900.5,"compra":null}"""))
        assertEquals(900.5, source.fetchVesPerRef(), 1e-9)
    }

    @Test
    fun `lanza error de parseo si no hay ningun valor`() = runTest {
        server.enqueue(MockResponse().setBody("""{"promedio":null,"venta":null,"compra":null}"""))
        try {
            source.fetchVesPerRef()
            fail("Debió lanzar ScraperParseException")
        } catch (e: ScraperParseException) {
            // esperado
        }
    }

    @Test
    fun `lanza error de parseo si el valor es cero`() = runTest {
        server.enqueue(MockResponse().setBody("""{"promedio":0.0}"""))
        try {
            source.fetchVesPerRef()
            fail("Debió lanzar ScraperParseException")
        } catch (e: ScraperParseException) {
            // esperado
        }
    }

    @Test
    fun `lanza error de parseo si el JSON es invalido`() = runTest {
        server.enqueue(MockResponse().setBody("<html>no es json</html>"))
        try {
            source.fetchVesPerRef()
            fail("Debió lanzar ScraperParseException")
        } catch (e: ScraperParseException) {
            // esperado
        }
    }

    @Test
    fun `propaga el codigo HTTP de error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            source.fetchVesPerRef()
            fail("Debió lanzar HttpStatusException")
        } catch (e: HttpStatusException) {
            assertEquals(500, e.code)
        }
    }
}
