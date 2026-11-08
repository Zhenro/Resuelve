package com.resuelve.preciosve.data.remote.gama

import com.resuelve.preciosve.data.remote.awaitString
import com.resuelve.preciosve.domain.bcv.BcvRateSource
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import com.resuelve.preciosve.domain.scraper.ScraperParseException
import com.resuelve.preciosve.domain.scraper.ScraperResult
import com.resuelve.preciosve.domain.scraper.SupermarketScraper
import com.resuelve.preciosve.domain.scraper.runScraper
import com.resuelve.preciosve.domain.util.LocalizedNumberParser
import java.net.URLEncoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Conector para Excelsior Gama (Sucursal Santa Eduvigis).
 * Consume el backend SAP Commerce OCC en egb2c-spa.
 * Extrae tanto productos de búsqueda con totalWithVatPrice (Total Ref. con IVA)
 * como la tasa oficial en currencies ("REF" -> conversion).
 */
class GamaScraper(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = DEFAULT_JSON
) : SupermarketScraper, BcvRateSource {

    override val store: Store = Store.GAMA
    override val name: String = "Gama (OCC)"

    override suspend fun search(query: String): ScraperResult = runScraper(store) {
        if (query.isBlank()) return@runScraper emptyList()

        val encodedFields = URLEncoder.encode(SEARCH_FIELDS, "UTF-8")
        val url = "$baseUrl/occ/v2/egb2c-spa/products/search?fields=$encodedFields&query=${URLEncoder.encode(query, "UTF-8")}&lang=es&curr=REF&warehouse=$DEFAULT_WAREHOUSE"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        val responseBody = client.newCall(request).awaitString()
        val dto = try {
            json.decodeFromString<GamaSearchResponseDto>(responseBody)
        } catch (e: SerializationException) {
            throw ScraperParseException("Error al deserializar catálogo de Gama", e)
        }

        dto.products?.mapNotNull { it.toDomain() } ?: emptyList()
    }

    override suspend fun fetchVesPerRef(): Double {
        val url = "$baseUrl/occ/v2/egb2c-spa/currencies"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        val responseBody = client.newCall(request).awaitString()
        val dto = try {
            json.decodeFromString<GamaCurrenciesResponseDto>(responseBody)
        } catch (e: SerializationException) {
            throw ScraperParseException("Error al deserializar currencies de Gama", e)
        }

        val refCurrency = dto.currencies?.firstOrNull { it.isocode.equals("REF", ignoreCase = true) }
        val conversionStr = refCurrency?.conversion
            ?: throw ScraperParseException("Moneda REF no encontrada en currencies de Gama")

        return LocalizedNumberParser.parse(conversionStr)?.takeIf { it > 0.0 }
            ?: throw ScraperParseException("Conversión de tasa BCV en Gama inválida: $conversionStr")
    }

    private fun GamaProductDto.toDomain(): ProductItem? {
        val cleanName = name
            ?.replace(HTML_TAG_REGEX, "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        // En Gama, totalWithVatPrice representa el precio final con IVA incluido (Total Ref.)
        // Si no está disponible o viene en 0, se recurre a price.value.
        val totalPrice = totalWithVatPrice?.value
            ?: totalWithVatPrice?.formattedValue?.let { LocalizedNumberParser.parse(it) }
            ?: price?.value
            ?: price?.formattedValue?.let { LocalizedNumberParser.parse(it) }
            ?: return null

        if (!totalPrice.isFinite() || totalPrice <= 0.0) return null

        val taxAmount = taxWithDiscount?.value ?: 0.0
        val basePrice = (totalPrice - taxAmount).coerceAtLeast(0.0)

        val imagePath = images?.firstOrNull { it.format == "product" || it.imageType == "PRIMARY" }?.url
            ?: images?.firstOrNull()?.url
        val imageUrl = imagePath?.let {
            if (it.startsWith("http")) it else "$baseUrl$it"
        }.orEmpty()

        val productUrl = if (!seoName.isNullOrBlank()) {
            "https://gamaenlinea.com/es/$seoName/p/$code"
        } else {
            "https://gamaenlinea.com/es/p/$code"
        }

        return ProductItem(
            id = "GAMA-$code",
            store = Store.GAMA,
            name = cleanName,
            basePriceRef = basePrice,
            totalPriceRef = totalPrice,
            totalPriceVes = 0.0,
            imageUrl = imageUrl,
            productUrl = productUrl
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.cl94ncbhsi-excelsior1-p1-public.model-t.cc.commerce.ondemand.com"
        const val DEFAULT_WAREHOUSE = "S007" // Santa Eduvigis
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
        private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
        private const val SEARCH_FIELDS = "products(dvMessage,score,baseProduct,taxWithDiscount(formattedValue,value),seoName,code,name,summary,configurable,configuratorType,multidimensional,price(FULL),images(FULL),stock(FULL),averageRating,variantOptions,vatAmountPrice(formattedValue),totalWithVatPrice(formattedValue,value),totalPriceWithNoDiscount(formattedValue),basePriceWithDiscount(formattedValue),categories(code,name),promotions(code,name,message,promotionType,labelColor,labelTextColor,dvMessage)),facets,breadcrumbs,pagination(DEFAULT),sorts(DEFAULT),freeTextSearch,currentQuery"
    }
}
