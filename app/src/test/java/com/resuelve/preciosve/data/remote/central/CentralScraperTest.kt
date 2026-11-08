package com.resuelve.preciosve.data.remote.central

import com.resuelve.preciosve.domain.model.Store
import com.resuelve.preciosve.domain.scraper.ScraperResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CentralScraperTest {

    private lateinit var server: MockWebServer
    private lateinit var scraper: CentralScraper

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scraper = CentralScraper(
            client = OkHttpClient(),
            baseUrl = server.url("").toString().removeSuffix("/")
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parsea respuesta html de busqueda extrayendo precio ins sobre del`() = runTest {
        val html = javaClass.classLoader!!.getResourceAsStream("fixtures/central_search.html")!!
            .bufferedReader().use { it.readText() }

        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val result = scraper.search("harina")
        assertTrue(result is ScraperResult.Success)
        val success = result as ScraperResult.Success
        assertEquals(Store.CENTRAL, success.store)
        assertTrue(success.items.isNotEmpty())

        val first = success.items[0]
        assertEquals("Harina Pan Integral 1Kg", first.name)
        // El primer producto tiene <del>1,49</del> y <ins>1,03</ins>
        assertEquals(1.03, first.totalPriceRef, 1e-9)
        assertEquals(1.49, first.basePriceRef, 1e-9)
        assertEquals("CENTRAL-45062", first.id)
    }

    @Test
    fun `sanitizeImageUrl descarta imagenes de marcas incompatibles como arroz mary en oreo`() {
        val corruptedOreoImg = CentralScraper.sanitizeImageUrl(
            "https://tucentralonline.com/uploads/arroz-mary-1kg.jpg",
            "Galleta Nabisco Oreo Tipo Americano 48Gr"
        )
        assertEquals("", corruptedOreoImg)

        val validOreoImg = CentralScraper.sanitizeImageUrl(
            "https://tucentralonline.com/uploads/oreo-americano.jpg",
            "Galleta Nabisco Oreo Tipo Americano 48Gr"
        )
        assertEquals("https://tucentralonline.com/uploads/oreo-americano.jpg", validOreoImg)
    }
}
