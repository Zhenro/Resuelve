package com.resuelve.preciosve.data.remote

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.random.Random
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reintentos automáticos con *exponential backoff* y jitter.
 *
 * - Reintenta ante errores de E/S transitorios y códigos 408, 425, 429, 500, 502, 503 y 504.
 * - No reintenta errores de certificado, interrupciones de hilo ni peticiones de cuerpo de un solo uso.
 * - Respeta la cabecera `Retry-After` (en segundos), limitada a [MAX_RETRY_AFTER_MS].
 * - Se detiene de inmediato si la llamada fue cancelada.
 *
 * @param maxAttempts Intentos totales (incluido el primero).
 * @param sleeper Función de espera; inyectable para pruebas sin dormir el hilo.
 */
class RetryInterceptor(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    private val jitterRatio: Double = DEFAULT_JITTER_RATIO,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val random: Random = Random.Default
) : Interceptor {

    init {
        require(maxAttempts >= 1) { "maxAttempts debe ser >= 1" }
        require(initialDelayMs >= 0L) { "initialDelayMs debe ser >= 0" }
        require(maxDelayMs >= initialDelayMs) { "maxDelayMs debe ser >= initialDelayMs" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio debe estar entre 0 y 1" }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val replayable = request.body?.isOneShot() != true
        var attempt = 1
        while (true) {
            if (chain.call().isCanceled()) throw IOException("Canceled")

            val delayMs: Long = try {
                val response = chain.proceed(request)
                val retryable = response.code in RETRYABLE_STATUS_CODES
                if (!replayable || !retryable || attempt >= maxAttempts) return response
                val computed = computeDelayMs(attempt, parseRetryAfterMs(response.header("Retry-After")))
                response.close()
                computed
            } catch (e: IOException) {
                if (!replayable || attempt >= maxAttempts || chain.call().isCanceled() || !isRetryable(e)) {
                    throw e
                }
                computeDelayMs(attempt, null)
            }

            pause(delayMs)
            attempt++
        }
    }

    /** Espera del intento [attempt] (1 = tras el primer fallo): inicial × 2^(n-1), con tope y jitter. */
    internal fun computeDelayMs(attempt: Int, retryAfterMs: Long?): Long {
        if (retryAfterMs != null) return retryAfterMs.coerceIn(0L, MAX_RETRY_AFTER_MS)
        val shift = (attempt - 1).coerceIn(0, MAX_SHIFT)
        val exponential = (initialDelayMs shl shift).coerceAtMost(maxDelayMs)
        val jitterSpan = (exponential * jitterRatio).toLong()
        val jitter = if (jitterSpan > 0L) random.nextLong(-jitterSpan, jitterSpan + 1L) else 0L
        return (exponential + jitter).coerceAtLeast(0L)
    }

    private fun parseRetryAfterMs(header: String?): Long? =
        header?.trim()?.toLongOrNull()?.let { it * 1_000L }

    private fun isRetryable(e: IOException): Boolean = when (e) {
        is SocketTimeoutException -> true
        is InterruptedIOException -> false
        is SSLPeerUnverifiedException -> false
        is SSLHandshakeException -> e.cause !is CertificateException
        else -> true
    }

    private fun pause(delayMs: Long) {
        if (delayMs <= 0L) return
        try {
            sleeper(delayMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrumpido durante el backoff")
        }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_INITIAL_DELAY_MS = 500L
        const val DEFAULT_MAX_DELAY_MS = 4_000L
        const val DEFAULT_JITTER_RATIO = 0.25
        const val MAX_RETRY_AFTER_MS = 10_000L
        private const val MAX_SHIFT = 20
        private val RETRYABLE_STATUS_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
    }
}
