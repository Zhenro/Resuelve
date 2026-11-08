package com.resuelve.preciosve.data.remote.forum

import com.resuelve.preciosve.data.remote.awaitString
import com.resuelve.preciosve.domain.bcv.BcvRateSource
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import com.resuelve.preciosve.domain.scraper.ScraperParseException
import com.resuelve.preciosve.domain.scraper.ScraperResult
import com.resuelve.preciosve.domain.scraper.SupermarketScraper
import com.resuelve.preciosve.domain.scraper.runScraper
import java.net.URLEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Conector para Forum Supermayorista vía Zupper API.
 * Consume directamente la API REST de Zupper (comercio 313: Plaza Venezuela).
 * Los precios se expresan en price_e2 (centavos de dólar / Ref).
 */
class ForumZupperScraper(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val companyId: Int = DEFAULT_COMPANY_ID,
    private val branchId: Int = DEFAULT_BRANCH_ID,
    private val json: Json = DEFAULT_JSON
) : SupermarketScraper, BcvRateSource {

    override val store: Store = Store.FORUM
    override val name: String = "Forum (Zupper)"

    override suspend fun search(query: String): ScraperResult = runScraper(store) {
        if (query.isBlank()) return@runScraper emptyList()

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/api/v3/companies/$companyId/branches/$branchId/goods?search=$encodedQuery"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        val responseBody = client.newCall(request).awaitString()
        val dto = try {
            json.decodeFromString<ForumGoodsResponseDto>(responseBody)
        } catch (e: SerializationException) {
            throw ScraperParseException("Error al deserializar catálogo de Forum Zupper", e)
        }

        dto.data?.mapNotNull { it.toDomain() } ?: emptyList()
    }

    override suspend fun fetchVesPerRef(): Double {
        val url = "$baseUrl/api/v3/companies/$companyId"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        val responseBody = client.newCall(request).awaitString()
        val dto = try {
            json.decodeFromString<ZupperCompanyResponseDto>(responseBody)
        } catch (e: SerializationException) {
            throw ScraperParseException("Error al deserializar datos de empresa en Zupper", e)
        }

        val vesCurrency = dto.currencies?.firstOrNull { it.iso.equals("VES", ignoreCase = true) }
        val factor = vesCurrency?.conversion_factor
            ?: throw ScraperParseException("Moneda VES no encontrada en Zupper company")

        if (!factor.isFinite() || factor <= 0.0) {
            throw ScraperParseException("Factor de conversión inválido en Zupper: $factor")
        }

        return factor
    }

    private fun ForumGoodDto.toDomain(): ProductItem? {
        val rawName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val cents = price_e2 ?: return null
        if (cents <= 0) return null

        val priceRef = cents / 100.0
        val imageUrl = picture_urls?.firstOrNull().orEmpty()
        val productUrl = "https://market.zupper.io/comercios/categorias/mercados/forum-supermayorista---plaza-venezuela-$branchId"

        return ProductItem(
            id = "FORUM-$id",
            store = Store.FORUM,
            name = rawName,
            basePriceRef = priceRef,
            totalPriceRef = priceRef,
            totalPriceVes = 0.0,
            imageUrl = imageUrl,
            productUrl = productUrl
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api-cl.zupper.io"
        const val DEFAULT_COMPANY_ID = 7
        const val DEFAULT_BRANCH_ID = 313
        private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class ZupperCompanyResponseDto(
    val currencies: List<ZupperCurrencyDto>? = null
)

@Serializable
internal data class ZupperCurrencyDto(
    val iso: String,
    val conversion_factor: Double? = null
)
