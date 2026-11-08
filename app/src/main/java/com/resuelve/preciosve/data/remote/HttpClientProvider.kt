package com.resuelve.preciosve.data.remote

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Proveedor Singleton del único [OkHttpClient] de la app (compartido por todos los conectores).
 *
 * - Timeouts estrictos: 10 s de conexión, 15 s de lectura/escritura.
 * - Connection pooling (5 conexiones inactivas, 5 min) y HTTP/2 para reutilizar sockets.
 * - GZIP transparente: OkHttp envía `Accept-Encoding: gzip` y descomprime la respuesta.
 * - Cabeceras de navegador móvil y reintentos con backoff exponencial.
 * - Concurrencia limitada para no saturar la CPU/red de equipos modestos.
 */
object HttpClientProvider {

    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 15L
    const val WRITE_TIMEOUT_SECONDS = 15L

    private const val MAX_IDLE_CONNECTIONS = 5
    private const val KEEP_ALIVE_MINUTES = 5L
    private const val MAX_REQUESTS = 12
    private const val MAX_REQUESTS_PER_HOST = 6

    /** Instancia compartida, creada de forma perezosa y segura entre hilos. */
    val client: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { createClient() }

    /**
     * Construye un cliente con la configuración estándar de la app. Se expone para pruebas
     * (permite inyectar un [RetryInterceptor] sin esperas reales); en producción use [client].
     */
    fun createClient(retryInterceptor: Interceptor = RetryInterceptor()): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = MAX_REQUESTS
                    maxRequestsPerHost = MAX_REQUESTS_PER_HOST
                }
            )
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(HeadersInterceptor())
            .addInterceptor(retryInterceptor)
            .build()
}
