package com.resuelve.preciosve.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpClientProviderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `el cliente es un singleton`() {
        assertSame(HttpClientProvider.client, HttpClientProvider.client)
    }

    @Test
    fun `aplica los timeouts estrictos de 10s y 15s`() {
        val client = HttpClientProvider.createClient()
        assertEquals(TimeUnit.SECONDS.toMillis(10).toInt(), client.connectTimeoutMillis)
        assertEquals(TimeUnit.SECONDS.toMillis(15).toInt(), client.readTimeoutMillis)
        assertEquals(TimeUnit.SECONDS.toMillis(15).toInt(), client.writeTimeoutMillis)
    }

    @Test
    fun `envia cabeceras de navegador movil y acepta gzip`() {
        server.enqueue(MockResponse().setBody("ok"))

        HttpClientProvider.createClient().newCall(Request.Builder().url(server.url("/")).build())
            .execute().close()

        val recorded = server.takeRequest()
        assertEquals("es-VE,es;q=0.9", recorded.getHeader("Accept-Language"))
        val userAgent = recorded.getHeader("User-Agent")
        assertNotNull(userAgent)
        assertTrue(userAgent!!.contains("Android") && userAgent.contains("Mobile"))
        assertTrue(recorded.getHeader("Accept-Encoding")!!.contains("gzip"))
    }

    @Test
    fun `no sobreescribe cabeceras definidas por la peticion`() {
        server.enqueue(MockResponse().setBody("ok"))
        val request = Request.Builder()
            .url(server.url("/"))
            .header("Accept", "application/json")
            .header("User-Agent", "custom-agent")
            .build()

        HttpClientProvider.createClient().newCall(request).execute().close()

        val recorded = server.takeRequest()
        assertEquals("application/json", recorded.getHeader("Accept"))
        assertEquals("custom-agent", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `descomprime respuestas gzip de forma transparente`() {
        val plain = "Harina PAN 1 kg"
        val compressed = Buffer()
        GzipSink(compressed).buffer().use { it.writeUtf8(plain) }
        server.enqueue(MockResponse().addHeader("Content-Encoding", "gzip").setBody(compressed))

        val body = HttpClientProvider.createClient()
            .newCall(Request.Builder().url(server.url("/")).build())
            .execute().use { it.body!!.string() }

        assertEquals(plain, body)
    }
}
