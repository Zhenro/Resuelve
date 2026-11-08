package com.resuelve.preciosve.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Añade cabeceras realistas de navegador móvil moderno para evitar bloqueos anti-bot.
 * Solo completa las que la petición no definió explícitamente.
 *
 * No se fija `Accept-Encoding` a propósito: si se hiciera manualmente, OkHttp dejaría de
 * descomprimir GZIP de forma transparente. OkHttp ya lo envía y lo descomprime solo.
 */
class HeadersInterceptor(
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val acceptLanguage: String = DEFAULT_ACCEPT_LANGUAGE,
    private val accept: String = DEFAULT_ACCEPT
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
        if (original.header("User-Agent") == null) builder.header("User-Agent", userAgent)
        if (original.header("Accept-Language") == null) builder.header("Accept-Language", acceptLanguage)
        if (original.header("Accept") == null) builder.header("Accept", accept)
        return chain.proceed(builder.build())
    }

    companion object {
        const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        const val DEFAULT_ACCEPT_LANGUAGE: String = "es-VE,es;q=0.9"
        const val DEFAULT_ACCEPT: String =
            "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7"
    }
}
