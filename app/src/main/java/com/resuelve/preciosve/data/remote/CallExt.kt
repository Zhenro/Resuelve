package com.resuelve.preciosve.data.remote

import com.resuelve.preciosve.domain.scraper.HttpStatusException
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/**
 * Ejecuta la llamada sin bloquear el hilo. Si la corrutina se cancela, la llamada HTTP también
 * se cancela y cualquier respuesta tardía se cierra para no filtrar conexiones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (ignored: Throwable) {
            // Cancelar una llamada ya terminada no es un error.
        }
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, _, _ -> response.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
    })
}

/** Como [await], pero lanza [HttpStatusException] (cerrando la respuesta) si el código no es 2xx. */
suspend fun Call.awaitSuccessful(): Response {
    val response = await()
    if (response.isSuccessful) return response
    val code = response.code
    val reason = response.message
    response.close()
    throw HttpStatusException(code, reason)
}

/** Ejecuta la llamada y devuelve el cuerpo como texto; lanza [IOException] si no hay cuerpo. */
suspend fun Call.awaitString(): String =
    awaitSuccessful().use { response ->
        response.body?.string() ?: throw IOException("Respuesta sin cuerpo")
    }
