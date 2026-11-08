package com.resuelve.preciosve.domain.util

import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ProductItem
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/**
 * Entidad normalizada con atributos estructurados para resolución y deduplicación
 * de productos equivalentes entre supermercados (Excelsior Gama, Forum, Central).
 */
data class NormalizedProduct(
    val rawItem: ProductItem,
    val brand: String?,
    val productType: String?,
    val variantFlavor: String?,
    val weightGramsOrMl: Double?,
    val weightFormatted: String?,
    val canonicalSlug: String,
    val normalizedTokens: Set<String>,
    val coreTokens: Set<String>
)

/**
 * Pipeline de Normalización, Extracción de Atributos y Resolución de Entidades (Entity Resolution).
 *
 * Descompone títulos crudos en 4 campos estructurados (marca, tipo_producto, variante_sabor, peso_normalizado),
 * resuelve sinónimos (ej: dulce/natural en yogures, choc/chocolate), genera una huella única (canonical_slug)
 * indiferente al orden de las palabras, y valida rigurosamente las imágenes para evitar imágenes cruzadas.
 */
object ProductEntityResolver {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val PUNCTUATION_REGEX = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    // Diccionario de marcas reconocidas en el mercado venezolano
    val KNOWN_BRANDS = setOf(
        "puig", "halls", "kraft", "nestle", "savoy", "nabisco", "oreo", "mary", "polar", "pan",
        "mavesa", "primor", "rona", "kelloggs", "quaker", "colgate", "palmolive", "protex", "dove",
        "pantene", "sedal", "ariel", "ace", "clorox", "pepsi", "coca cola", "chinotto", "hit",
        "yukery", "maltin", "solera", "regional", "santa teresa", "cacique", "pampero", "plumrose",
        "oscar mayer", "arichuna", "la montserratina", "tovar", "torondoy", "paisa", "bufalinda",
        "renata", "danibisk", "charmy", "flora", "naturoil", "vatel", "mazeite", "diana", "capri",
        "allegri", "ronco", "la especial", "toddy", "chocolisto", "nesquik", "flips", "pirulin",
        "susy", "cocosette", "samba", "galak", "carre", "toronto", "ping pong", "morochas", "maria",
        "club social", "kraker", "ritz", "chips ahoy", "gullon", "nature valley", "chupa chups",
        "yoka", "migurt", "parmalat", "campestre", "tapa roja", "juana", "harina pan"
    )

    // Palabras vacías que no aportan a la identidad del producto
    private val STOP_WORDS = setOf(
        "de", "del", "la", "el", "los", "las", "en", "para", "con", "sin", "d", "y", "e",
        "un", "una", "unos", "unas", "paquete", "paquetes", "paq", "pqte", "pack",
        "unidad", "unidades", "unid", "und", "pieza", "pza", "display", "cu",
        "sabor", "presentacion", "art", "articulo", "original", "tipo", "clasico", "clasica"
    )

    // Diccionario de sinónimos, correcciones fonéticas de errores tipográficos y expansiones semánticas
    private val TOKEN_SYNONYMS = mapOf(
        "arina" to "harina",
        "mayo" to "mayonesa",
        "mayonesas" to "mayonesa",
        "margarinas" to "margarina",
        "mantequilla" to "margarina",
        "arroses" to "arroz",
        "aceites" to "aceite",
        "pastas" to "pasta",
        "cafes" to "cafe",
        "azucares" to "azucar",
        "cereales" to "cereal",
        "jugos" to "jugo",
        "refrescos" to "refresco",
        "cocacola" to "coca cola",
        "pepsicola" to "pepsi",
        "torondo" to "torondoy",
        "campina" to "campiña",
        "choc" to "chocolate",
        "chocolat" to "chocolate",
        "vain" to "vainilla",
        "vanilla" to "vainilla",
        "fres" to "fresa",
        "galletas" to "galleta",
        "galleticas" to "galleta",
        "caramelos" to "caramelo",
        "leches" to "leche",
        "rellenas" to "relleno",
        "rellenos" to "relleno",
        "integrales" to "integral",
        "blanca" to "blanco",
        "blancas" to "blanco",
        "blancos" to "blanco",
        "negra" to "negro",
        "negras" to "negro",
        "negros" to "negro",
        "condensada" to "condensada",
        "cond" to "condensada",
        "evaporada" to "evaporada",
        "evap" to "evaporada",
        "cremas" to "crema",
        "tradicional" to "tradicional",
        "trad" to "tradicional",
        "familiar" to "familiar",
        "fam" to "familiar",
        "p.a.n." to "pan",
        "p.a.n" to "pan"
    )

    // Diccionario de sinónimos para variantes y sabores
    private val FLAVOR_SYNONYMS = mapOf(
        "dulce" to "natural", // En yogures venezolanos: Firme Dulce == Firme Natural
        "choc" to "chocolate",
        "chocolat" to "chocolate",
        "vain" to "vainilla",
        "vanilla" to "vainilla",
        "fres" to "fresa",
        "tradicional" to "clasico",
        "regular" to "clasico",
        "original" to "clasico",
        "americano" to "americano",
        "descremada" to "descremado",
        "light" to "descremado",
        "entera" to "entera",
        "completa" to "entera"
    )

    // Regex para extracción de unidades de peso y volumen estandarizadas
    private val WEIGHT_REGEXES = listOf(
        Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(?:kilos?|kilogramos?|kg)\\b") to 1000.0,
        Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(?:gramos?|grs?|gr\\.?|g)\\b") to 1.0,
        Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(?:litros?|lts?|lt\\.?|l)\\b") to 1000.0,
        Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(?:mililitros?|mls?|ml|cc)\\b") to 1.0
    )

    /**
     * Normaliza un [ProductItem] descomponiéndolo en 4 campos estrictos y generando el canonicalSlug.
     */
    fun normalize(item: ProductItem): NormalizedProduct {
        val rawName = item.name.lowercase(Locale.ROOT)

        // 1. Extraer peso_normalizado estructurado y removerlo del texto para evitar tokens residuales (150g vs 150gr)
        var extractedWeight: Double? = null
        var weightStr: String? = null
        var textWithoutWeight = rawName

        for ((regex, multiplier) in WEIGHT_REGEXES) {
            val match = regex.find(rawName)
            if (match != null) {
                val numStr = match.groupValues[1].replace(',', '.')
                val num = numStr.toDoubleOrNull()
                if (num != null && num > 0) {
                    val inBaseUnits = num * multiplier
                    extractedWeight = inBaseUnits
                    weightStr = formatWeight(inBaseUnits, multiplier == 1000.0 && rawName.contains(Regex("[kK]g|[lL]t?")))
                    textWithoutWeight = regex.replace(rawName, " ")
                    break
                }
            }
        }

        // 2. Limpieza de diacríticos y caracteres especiales
        var text = textWithoutWeight
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
        text = DIACRITICS_REGEX.replace(text, "")
        text = text.replace("p.a.n.", "pan").replace("p.a.n", "pan")
        text = text.replace(Regex("\\b(?:s/\\s*gluten|sin\\s+gluten|gluten\\s*free|libre\\s+de\\s+gluten)\\b"), "singluten")
        text = text.replace(Regex("\\b(?:s/\\s*azucar|sin\\s+azucar|sugar\\s*free)\\b"), "sinazucar")
        text = PUNCTUATION_REGEX.replace(text, " ")

        // 3. Tokenización y descomposición estructurada
        val rawTokens = MULTI_SPACE_REGEX.split(text)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val normalizedTokens = mutableSetOf<String>()
        var detectedBrand: String? = null
        var detectedFlavor: String? = null

        for (token in rawTokens) {
            val expanded = TOKEN_SYNONYMS[token] ?: token

            // Detectar marca en diccionario
            if (detectedBrand == null && expanded in KNOWN_BRANDS) {
                detectedBrand = expanded
            }

            // Detectar variante / sabor
            if (detectedFlavor == null && expanded in FLAVOR_SYNONYMS) {
                detectedFlavor = FLAVOR_SYNONYMS[expanded]
            }

            if (expanded !in STOP_WORDS && (expanded.length > 1 || expanded.all { it.isDigit() })) {
                val canonicalToken = FLAVOR_SYNONYMS[expanded] ?: expanded
                normalizedTokens.add(canonicalToken)
            }
        }

        // Si encontramos peso normalizado, incluirlo como token estándar
        weightStr?.let { normalizedTokens.add(it) }

        // Canonical slug: huella única e indiferente al orden de las palabras
        val canonicalSlug = normalizedTokens.sorted().joinToString(" ")

        // Core tokens: tokens excluyendo la marca para comparación agnóstica de fabricante/marca
        val coreTokens = if (detectedBrand != null) {
            normalizedTokens.filter { it != detectedBrand }.toSet()
        } else {
            normalizedTokens
        }

        return NormalizedProduct(
            rawItem = item,
            brand = detectedBrand,
            productType = null,
            variantFlavor = detectedFlavor,
            weightGramsOrMl = extractedWeight,
            weightFormatted = weightStr,
            canonicalSlug = canonicalSlug,
            normalizedTokens = normalizedTokens,
            coreTokens = coreTokens
        )
    }

    /**
     * Determina si dos productos representan la misma entidad física (mismo producto en diferentes tiendas).
     */
    fun areSameEntity(a: NormalizedProduct, b: NormalizedProduct): Boolean {
        // Regla 0: Coincidencia exacta de canonical slug
        if (a.canonicalSlug.isNotBlank() && a.canonicalSlug == b.canonicalSlug) {
            return true
        }

        // Regla 1: Gramaje incompatible descarta la fusión
        if (a.weightGramsOrMl != null && b.weightGramsOrMl != null) {
            if (abs(a.weightGramsOrMl - b.weightGramsOrMl) > 2.0) {
                return false
            }
        }

        // Regla 2: Variantes de sabor incompatibles no deben fusionarse (ej: durazno vs fresa)
        if (a.variantFlavor != null && b.variantFlavor != null && a.variantFlavor != b.variantFlavor) {
            return false
        }

        // Regla 3: Marcas incompatibles
        if (a.brand != null && b.brand != null && a.brand != b.brand) {
            val isCharmyPuig = (a.brand == "puig" && b.brand == "charmy") || (a.brand == "charmy" && b.brand == "puig")
            val isOreoNabisco = (a.brand == "nabisco" && b.brand == "oreo") || (a.brand == "oreo" && b.brand == "nabisco")
            if (!isCharmyPuig && !isOreoNabisco) {
                return false
            }
        }

        // Regla 4: Similitud de Jaccard sobre tokens normalizados (>= 75%)
        val intersection = a.normalizedTokens.intersect(b.normalizedTokens)
        val union = a.normalizedTokens.union(b.normalizedTokens)

        if (union.isNotEmpty()) {
            val jaccard = intersection.size.toDouble() / union.size.toDouble()
            if (jaccard >= 0.75) {
                return true
            }
        }

        // Regla 5: Similitud de contención / Overlap sobre coreTokens (>= 85%)
        val coreIntersection = a.coreTokens.intersect(b.coreTokens)
        val minCoreSize = min(a.coreTokens.size, b.coreTokens.size)

        if (minCoreSize >= 2) {
            val coreOverlap = coreIntersection.size.toDouble() / minCoreSize.toDouble()
            if (coreOverlap >= 0.85) {
                return true
            }
        }

        return false
    }

    /**
     * Valida que una URL de imagen pertenezca legítimamente al producto y no a una marca ajena (anti-cruce).
     */
    fun isImageValidForProduct(imageUrl: String, brand: String?): Boolean {
        if (imageUrl.isBlank()) return false
        val lowerUrl = imageUrl.lowercase(Locale.ROOT)

        if (lowerUrl.contains("woocommerce-placeholder") ||
            lowerUrl.contains("placeholder") ||
            lowerUrl.startsWith("data:image") ||
            lowerUrl.contains("blank.gif")
        ) {
            return false
        }

        // Si la marca es conocida, rechazar imágenes que mencionen marcas competidoras incompatibles
        if (brand != null) {
            if (brand == "oreo" && (lowerUrl.contains("mary") || lowerUrl.contains("arroz") || lowerUrl.contains("harina"))) {
                return false
            }
            if (brand == "yoka" && (lowerUrl.contains("mary") || lowerUrl.contains("arroz") || lowerUrl.contains("harina"))) {
                return false
            }

            val crossBrands = listOf("mary", "pan", "mavesa", "primor", "harina-pan", "arroz-mary")
            for (cb in crossBrands) {
                if (brand != cb && cb !in brand && lowerUrl.contains(cb)) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * Selecciona la mejor imagen verificada dentro de una lista de ofertas para un producto maestro.
     */
    fun selectVerifiedImage(items: List<ProductItem>, brand: String?): String {
        for (item in items) {
            if (isImageValidForProduct(item.imageUrl, brand)) {
                return item.imageUrl
            }
        }
        return ""
    }

    /**
     * Agrupa una lista de productos en clusters de entidades equivalentes ([ComparisonGroup]),
     * deduplicando por supermercado y asociando una imagen verificada y un canonical_slug unificado.
     */
    fun clusterProducts(
        products: List<ProductItem>,
        rate: com.resuelve.preciosve.domain.model.BcvRate?
    ): List<ComparisonGroup> {
        if (products.isEmpty()) return emptyList()

        val normalizedList = products.map { normalize(it.withBcvRate(rate)) }
        val clusters = mutableListOf<MutableList<NormalizedProduct>>()

        for (item in normalizedList) {
            var matchedCluster: MutableList<NormalizedProduct>? = null

            for (cluster in clusters) {
                val representative = cluster.first()
                if (areSameEntity(representative, item)) {
                    matchedCluster = cluster
                    break
                }
            }

            if (matchedCluster != null) {
                matchedCluster.add(item)
            } else {
                clusters.add(mutableListOf(item))
            }
        }

        return clusters.map { cluster ->
            val representative = cluster.maxByOrNull { it.rawItem.name.length } ?: cluster.first()
            val canonicalKey = representative.canonicalSlug.ifBlank {
                representative.normalizedTokens.sorted().joinToString(" ")
                    .ifBlank { representative.rawItem.name.trim().lowercase(Locale.ROOT) }
            }

            val displayName = NameNormalizer.cleanDisplayName(representative.rawItem.name)

            // Selección estricta de imagen verificada
            val verifiedImage = selectVerifiedImage(cluster.map { it.rawItem }, representative.brand)

            // Asignar imagen verificada a los items para visualización coherente
            val cleanedItems = cluster.map { norm ->
                if (norm.rawItem.imageUrl.isBlank() && verifiedImage.isNotBlank()) {
                    norm.rawItem.copy(imageUrl = verifiedImage)
                } else if (!isImageValidForProduct(norm.rawItem.imageUrl, representative.brand)) {
                    norm.rawItem.copy(imageUrl = verifiedImage)
                } else {
                    norm.rawItem
                }
            }

            ComparisonGroup.create(
                key = canonicalKey,
                displayName = displayName,
                items = cleanedItems
            )
        }
    }

    private fun formatWeight(gramsOrMl: Double, isKiloOrLiter: Boolean): String {
        return if (isKiloOrLiter || gramsOrMl >= 1000.0) {
            val k = gramsOrMl / 1000.0
            if (k % 1.0 == 0.0) "${k.toInt()}kg" else "${k}kg"
        } else {
            if (gramsOrMl % 1.0 == 0.0) "${gramsOrMl.toInt()}g" else "${gramsOrMl}g"
        }
    }

    /**
     * Calcula y formatea el precio por unidad de medida estándar ($/kg o $/100g o $/L).
     */
    fun calculateUnitPrice(totalPriceRef: Double, weightGramsOrMl: Double?): String? {
        if (weightGramsOrMl == null || weightGramsOrMl <= 0.0 || totalPriceRef <= 0.0) return null
        return if (weightGramsOrMl >= 1000.0) {
            val kg = weightGramsOrMl / 1000.0
            val pricePerKg = totalPriceRef / kg
            String.format(Locale.ROOT, "Ref. %.2f/kg", pricePerKg)
        } else if (weightGramsOrMl >= 250.0) {
            val kg = weightGramsOrMl / 1000.0
            val pricePerKg = totalPriceRef / kg
            String.format(Locale.ROOT, "Ref. %.2f/kg", pricePerKg)
        } else {
            val per100g = (totalPriceRef / weightGramsOrMl) * 100.0
            String.format(Locale.ROOT, "Ref. %.2f/100g", per100g)
        }
    }

    /**
     * Expansión de conceptos e intenciones de compra frecuentes en Venezuela.
     */
    fun expandSemanticQuery(query: String): List<String> {
        val clean = query.trim().lowercase(Locale.ROOT)
        return when (clean) {
            "desayuno" -> listOf("harina pan", "cafe", "leche", "queso", "margarina mavesa")
            "arepa", "arepas" -> listOf("harina pan", "margarina mavesa", "queso")
            "limpieza" -> listOf("clorox", "ace", "ariel", "jabon las llaves", "lavaplatos")
            "charcuteria" -> listOf("jamon plumrose", "queso paisa", "salchichas", "queso torondoy")
            "chucheria", "chucherias", "snacks" -> listOf("galletas charmy", "oreo", "flips", "pirulin")
            else -> listOf(clean)
        }
    }
}
