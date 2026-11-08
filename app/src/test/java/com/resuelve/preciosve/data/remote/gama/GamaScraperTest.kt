package com.resuelve.preciosve.data.remote.gama

import com.resuelve.preciosve.domain.model.Store
import com.resuelve.preciosve.domain.scraper.ScraperResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GamaScraperTest {

    private lateinit var server: MockWebServer
    private lateinit var scraper: GamaScraper

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scraper = GamaScraper(
            client = OkHttpClient(),
            baseUrl = server.url("").toString().removeSuffix("/")
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parsea respuesta de busqueda con totalWithVatPrice y sin etiquetas html`() = runTest {
        val json = javaClass.classLoader!!.getResourceAsStream("fixtures/gama_search.json")!!
            .bufferedReader().use { it.readText() }

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = scraper.search("harina")
        assertTrue(result is ScraperResult.Success)
        val success = result as ScraperResult.Success
        assertEquals(Store.GAMA, success.store)
        assertEquals(5, success.items.size)

        val first = success.items[0]
        assertEquals("GAMA-10035499", first.id)
        assertEquals("HARINA DE MAIZ BLANCO MARY 2 KG", first.name)
        assertEquals(2.0, first.totalPriceRef, 0.0)
        assertEquals(2.0, first.basePriceRef, 0.0)
        assertTrue(first.imageUrl.contains("96Wx96H"))

        val second = success.items[1]
        assertEquals("GAMA-10035338", second.id)
        assertEquals("HARINA DE MAIZ BLANCO MARY 900 GR", second.name)
        assertEquals(0.95, second.totalPriceRef, 0.0)
        assertEquals(0.95, second.basePriceRef, 0.0)
    }

    @Test
    fun `obtiene la tasa oficial de currencies REF`() = runTest {
        val json = javaClass.classLoader!!.getResourceAsStream("fixtures/gama_currencies.json")!!
            .bufferedReader().use { it.readText() }

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val rate = scraper.fetchVesPerRef()
        assertEquals(880.12, rate, 1e-9)
    }
}
