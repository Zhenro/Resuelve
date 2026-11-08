package com.resuelve.preciosve.data.remote

import com.resuelve.preciosve.domain.scraper.HttpStatusException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RetryInterceptorTest {

    private lateinit var server: MockWebServer
    private val delays = mutableListOf<Long>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        delays.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client(maxAttempts: Int = 3): OkHttpClient =
        OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor(
                RetryInterceptor(
                    maxAttempts = maxAttempts,
                    initialDelayMs = 500L,
                    maxDelayMs = 4_000L,
                    jitterRatio = 0.0,
                    sleeper = { delays += it }
                )
            )
            .build()

    private fun request() = Request.Builder().url(server.url("/")).build()

    @Test
    fun `reintenta 503 con backoff exponencial hasta tener exito`() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        client().newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("ok", response.body!!.string())
        }
        assertEquals(3, server.requestCount)
        assertEquals(listOf(500L, 1_000L), delays)
    }

    @Test
    fun `tras agotar los intentos devuelve la ultima respuesta de error`() {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(502)) }

        client(maxAttempts = 3).newCall(request()).execute().use { response ->
            assertEquals(502, response.code)
        }
        assertEquals(3, server.requestCount)
        assertEquals(2, delays.size)
    }

    @Test
    fun `no reintenta errores 4xx no transitorios`() {
        server.enqueue(MockResponse().setResponseCode(404))

        client().newCall(request()).execute().use { response ->
            assertEquals(404, response.code)
        }
        assertEquals(1, server.requestCount)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun `respeta Retry-After en segundos`() {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "2"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        client().newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
        }
        assertEquals(listOf(2_000L), delays)
    }

    @Test
    fun `limita Retry-After excesivo al maximo permitido`() {
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "600"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        client().newCall(request()).execute().use { }
        assertEquals(listOf(RetryInterceptor.MAX_RETRY_AFTER_MS), delays)
    }

    @Test
    fun `reintenta ante fallo de conexion`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        client().newCall(request()).execute().use { response ->
            assertEquals("ok", response.body!!.string())
        }
        assertEquals(listOf(500L), delays)
    }

    @Test
    fun `propaga la excepcion cuando se agotan los intentos de red`() {
        repeat(2) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }

        try {
            client(maxAttempts = 2).newCall(request()).execute().close()
            fail("Debió lanzar IOException")
        } catch (e: java.io.IOException) {
            assertEquals(1, delays.size)
        }
    }

    @Test
    fun `el backoff se limita al maximo configurado`() {
        val interceptor = RetryInterceptor(
            maxAttempts = 10,
            initialDelayMs = 500L,
            maxDelayMs = 4_000L,
            jitterRatio = 0.0
        )
        assertEquals(500L, interceptor.computeDelayMs(1, null))
        assertEquals(1_000L, interceptor.computeDelayMs(2, null))
        assertEquals(2_000L, interceptor.computeDelayMs(3, null))
        assertEquals(4_000L, interceptor.computeDelayMs(4, null))
        assertEquals(4_000L, interceptor.computeDelayMs(9, null))
    }

    @Test
    fun `el jitter se mantiene dentro del rango esperado`() {
        val interceptor = RetryInterceptor(
            maxAttempts = 5,
            initialDelayMs = 1_000L,
            maxDelayMs = 8_000L,
            jitterRatio = 0.25
        )
        repeat(200) {
            val delay = interceptor.computeDelayMs(1, null)
            assertTrue("delay=$delay", delay in 750L..1_250L)
        }
    }

    @Test
    fun `awaitSuccessful lanza HttpStatusException ante codigos de error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val plain = OkHttpClient()
        try {
            plain.newCall(request()).awaitSuccessful()
            fail("Debió lanzar HttpStatusException")
        } catch (e: HttpStatusException) {
            assertEquals(403, e.code)
        }
    }

    @Test
    fun `awaitString devuelve el cuerpo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hola"))
        assertEquals("hola", OkHttpClient().newCall(request()).awaitString())
    }
}
