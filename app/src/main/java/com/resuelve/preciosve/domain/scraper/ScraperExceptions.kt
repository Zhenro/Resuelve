package com.resuelve.preciosve.domain.scraper

import java.io.IOException

/**
 * Se lanza cuando un servidor responde con un código HTTP distinto de 2xx.
 * Extiende [IOException] para que los interceptores y [ScraperError.from] la traten como fallo de red.
 */
class HttpStatusException(val code: Int, val reason: String) :
    IOException("HTTP $code $reason".trim())

/** Se lanza cuando la respuesta llegó pero no tiene la estructura esperada. */
class ScraperParseException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
