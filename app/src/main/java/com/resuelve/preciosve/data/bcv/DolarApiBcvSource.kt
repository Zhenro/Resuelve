package com.resuelve.preciosve.data.bcv

import com.resuelve.preciosve.data.remote.awaitString
import com.resuelve.preciosve.domain.bcv.BcvRateSource
import com.resuelve.preciosve.domain.scraper.ScraperParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fuente de tasa BCV basada en la API pública `ve.dolarapi.com` (dólar oficial).
 * Sirve como respaldo cuando la tasa de Gama no está disponible.
 */
class DolarApiBcvSource(
    private val client: OkHttpClient,
    private val url: String = DEFAULT_URL,
    private val json: Json = DEFAULT_JSON
) : BcvRateSource {

    override val name: String = "ve.dolarapi.com"

    override suspend fun fetchVesPerRef(): Double {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()
        val body = client.newCall(request).awaitString()
        val dto = try {
            json.decodeFromString<DolarApiResponse>(body)
        } catch (e: SerializationException) {
            throw ScraperParseException("Respuesta de la API de tasa BCV inválida", e)
        }
        val value = dto.promedio ?: dto.venta ?: dto.compra
        if (value == null || !value.isFinite() || value <= 0.0) {
            throw ScraperParseException("La API de tasa BCV no devolvió un valor válido")
        }
        return value
    }

    companion object {
        const val DEFAULT_URL = "https://ve.dolarapi.com/v1/dolares/oficial"
        private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class DolarApiResponse(
    val promedio: Double? = null,
    val venta: Double? = null,
    val compra: Double? = null,
    val fechaActualizacion: String? = null
)
