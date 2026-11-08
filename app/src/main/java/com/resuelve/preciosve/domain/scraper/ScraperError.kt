package com.resuelve.preciosve.domain.scraper

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException

/** Clasificación de fallos de un scraper, pensada para mostrar al usuario o decidir reintentos. */
sealed interface ScraperError {
    val message: String
    val cause: Throwable?

    /** Sin conexión, DNS, reset, o respuesta HTTP de error del servidor. */
    data class Network(
        override val message: String,
        override val cause: Throwable? = null
    ) : ScraperError

    /** El servidor no respondió dentro de los timeouts configurados. */
    data class Timeout(
        override val message: String,
        override val cause: Throwable? = null
    ) : ScraperError

    /** El portal rechazó la petición (401/403/429): probable bloqueo anti-bot o rate limit. */
    data class Blocked(
        val httpCode: Int,
        override val message: String,
        override val cause: Throwable? = null
    ) : ScraperError

    /** La respuesta llegó pero no se pudo interpretar (HTML o JSON inesperado). */
    data class Parse(
        override val message: String,
        override val cause: Throwable? = null
    ) : ScraperError

    /** Cualquier otro fallo no previsto. */
    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null
    ) : ScraperError

    companion object {

        private val BLOCKED_CODES = setOf(401, 403, 429)

        /**
         * Traduce una excepción a un [ScraperError]. No debe usarse con
         * `CancellationException` genérica: esa debe propagarse para respetar la cancelación.
         */
        fun from(throwable: Throwable): ScraperError {
            val message = throwable.message ?: throwable.javaClass.simpleName
            return when (throwable) {
                is HttpStatusException ->
                    if (throwable.code in BLOCKED_CODES) {
                        Blocked(throwable.code, message, throwable)
                    } else {
                        Network(message, throwable)
                    }
                is SocketTimeoutException -> Timeout(message, throwable)
                is TimeoutCancellationException -> Timeout(message, throwable)
                is ScraperParseException -> Parse(message, throwable)
                is IllegalArgumentException -> Parse(message, throwable)
                is IOException -> Network(message, throwable)
                else -> Unknown(message, throwable)
            }
        }
    }
}
