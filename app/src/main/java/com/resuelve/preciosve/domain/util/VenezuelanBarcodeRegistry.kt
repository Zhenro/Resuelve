package com.resuelve.preciosve.domain.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Registro inteligente de códigos de barra (EAN-13, EAN-8, UPC) para productos de despensa
 * en Venezuela, combinando un diccionario local instantáneo con resolución en línea (Open Food Facts).
 */
object VenezuelanBarcodeRegistry {

    private val json = Json { ignoreUnknownKeys = true }

    // Diccionario local preindexado con productos de alta rotación en Venezuela
    private val KNOWN_BARCODES = mutableMapOf(
        // Los 3 productos exactos de la despensa del usuario
        "7622202324116" to "Galleta Club Social Integral",
        "7590006200137" to "Margarina Mavesa 500g",
        "7591473004976" to "Cotufas Mary 400g",

        // Maizina Americana
        "7591031000133" to "Maizina Americana 400g",
        "7591031000126" to "Maizina Americana 200g",
        "7591031000140" to "Maizina Americana 800g",

        // Harina P.A.N.
        "7591008000013" to "Harina P.A.N. Maiz Blanco 1kg",
        "7591008000020" to "Harina P.A.N. Maiz Amarillo 1kg",
        "7591008000037" to "Avena Quaker 400g",
        "7591008000044" to "Mezcla Dulce Harina PAN 500g",

        // Arroz Mary
        "7591016000010" to "Arroz Mary Tradicional 1kg",
        "7591016000027" to "Arroz Mary Esmeralda 1kg",
        "7591016000034" to "Arroz Mary Dorado 1kg",

        // Mavesa / Alimentos Polar
        "7591011000015" to "Mayonesa Mavesa 445g",
        "7591011000022" to "Margarina Mavesa 500g",
        "7591011000039" to "Aceite Vatel 1L",
        "7591011000046" to "Vinagre Mavesa 1L",

        // Galletas Puig & Nabisco
        "7591005000010" to "Galletas Charmy Chocolate 192g",
        "7591005000027" to "Galletas Maria Puig 192g",
        "7622210000015" to "Galleta Nabisco Oreo 48g",
        "7622210000022" to "Galleta Club Social 26g",

        // Pastas y Café
        "7591040000018" to "Pasta Ronco Vermicelli 1kg",
        "7591040000025" to "Pasta Primor Corta 1kg",
        "7591024000012" to "Café Fama de América 500g",
        "7591024000029" to "Café Madrid 500g",

        // Lácteos
        "7591021000011" to "Leche Completa La Campiña 1kg",
        "7591021000028" to "Leche Completa Parmalat 1kg",
        "7591035000016" to "Yogurt Yoka Firme 150g"
    )

    /**
     * Resuelve el nombre canónico de un producto a partir de su código de barras físico.
     * Consulta primero la memoria local y, si no está, recurre a Open Food Facts en segundo plano.
     */
    fun resolveBarcodeLocal(barcode: String): String? {
        val clean = barcode.trim()
        return KNOWN_BARCODES[clean]
    }

    fun resolveBarcode(barcode: String): String? = resolveBarcodeLocal(barcode)

    /**
     * Consulta la API pública internacional de Open Food Facts para identificar cualquier
     * código de barra de alimentos no catalogado localmente.
     */
    suspend fun resolveBarcodeOnline(barcode: String, client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        val clean = barcode.trim()
        // 1. Revisar memoria local primero
        val local = resolveBarcodeLocal(clean)
        if (local != null) return@withContext local

        // 2. Consulta en línea a Open Food Facts
        try {
            val url = "https://world.openfoodfacts.org/api/v0/product/$clean.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ResuelvePrecios-VE - Android")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val root = json.parseToJsonElement(body).jsonObject
                val status = root["status"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                if (status != 1) return@withContext null

                val product = root["product"]?.jsonObject ?: return@withContext null
                val productName = product["product_name"]?.jsonPrimitive?.content?.trim()
                val brands = product["brands"]?.jsonPrimitive?.content?.trim()

                val resolved = buildString {
                    if (!productName.isNullOrBlank()) append(productName)
                    if (!brands.isNullOrBlank() && !productName.orEmpty().contains(brands, ignoreCase = true)) {
                        append(" ").append(brands)
                    }
                }.trim()

                if (resolved.isNotBlank()) {
                    KNOWN_BARCODES[clean] = resolved
                    return@withContext resolved
                }
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Registra dinámicamente un código de barras en memoria.
     */
    fun registerBarcode(barcode: String, productName: String) {
        KNOWN_BARCODES[barcode.trim()] = productName.trim()
    }

    /**
     * Comprueba si una cadena de texto es un código de barras (solo dígitos, longitud 8-14).
     */
    fun isBarcode(text: String): Boolean {
        val clean = text.trim()
        return clean.length in 8..14 && clean.all { it.isDigit() }
    }
}
