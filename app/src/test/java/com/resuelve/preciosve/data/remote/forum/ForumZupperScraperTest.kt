package com.resuelve.preciosve.data.remote.forum

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

class ForumZupperScraperTest {

    private lateinit var server: MockWebServer
    private lateinit var scraper: ForumZupperScraper

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scraper = ForumZupperScraper(
            client = OkHttpClient(),
            baseUrl = server.url("").toString().removeSuffix("/")
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parsea respuesta de busqueda transformando centavos a dolares`() = runTest {
        val json = javaClass.classLoader!!.getResourceAsStream("fixtures/forum_search.json")!!
            .bufferedReader().use { it.readText() }

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val result = scraper.search("harina")
        assertTrue(result is ScraperResult.Success)
        val success = result as ScraperResult.Success
        assertEquals(Store.FORUM, success.store)
        assertEquals(12, success.items.size)

        val first = success.items[0]
        assertEquals("FORUM-140900", first.id)
        assertEquals("Harina De Avena Quaker 400G", first.name)
        assertEquals(1.45, first.totalPriceRef, 1e-9) // 145 centavos = 1.45
        assertTrue(first.imageUrl.contains("good_140900"))

        val second = success.items[1]
        assertEquals(5.40, second.totalPriceRef, 1e-9) // 540 centavos = 5.40
    }

    @Test
    fun `obtiene la tasa oficial de la empresa en Zupper`() = runTest {
        val json = """
            {
               "currencies": [
                  {"iso": "USD", "conversion_factor": 1.0},
                  {"iso": "VES", "conversion_factor": 881.0664}
               ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val rate = scraper.fetchVesPerRef()
        assertEquals(881.0664, rate, 1e-9)
    }
}
