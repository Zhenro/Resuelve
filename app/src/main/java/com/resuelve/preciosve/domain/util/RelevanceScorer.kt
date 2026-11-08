package com.resuelve.preciosve.domain.util

import com.resuelve.preciosve.domain.model.ComparisonGroup
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/**
 * Motor de Scoring y Relevancia Estricta para Búsquedas.
 *
 * Jerarquía de Relevancia:
 * 1. Exact Match de Token (10.000+ pts): El token de búsqueda coincide exactamente con una palabra del producto o marca.
 * 2. Prefix Match (5.000 pts): Una palabra del producto comienza con el término buscado.
 * 3. Substring Match (2.500 pts): El término está contenido en alguna palabra.
 * 4. Fuzzy Match restringido (<= 500 pts): Distancia de Levenshtein (1 o 2 ediciones).
 *
 * Regla de Oro: Un fuzzy match ("cherry" para query "charmy") NUNCA superará a un exact match
 * ("charmy") por tener un precio menor. Solo cuando dos productos tienen la misma relevancia,
 * se ordenan de menor a mayor precio.
 */
object RelevanceScorer {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val PUNCTUATION_REGEX = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private val TYPO_MAP = mapOf(
        "arina" to "harina",
        "mayo" to "mayonesa",
        "torondo" to "torondoy",
        "campina" to "campiña",
        "cocacola" to "coca cola",
        "pepsicola" to "pepsi"
    )

    const val SCORE_EXACT_MATCH = 10_000
    const val SCORE_PREFIX_MATCH = 5_000
    const val SCORE_SUBSTRING_MATCH = 2_500
    const val SCORE_FUZZY_MATCH_1 = 500
    const val SCORE_FUZZY_MATCH_2 = 100
    const val SCORE_NONE = 0

    /**
     * Calcula la puntuación de relevancia de un [ComparisonGroup] con respecto a la consulta.
     */
    fun calculateScore(group: ComparisonGroup, query: String): Int {
        val cleanQuery = sanitize(query)
        if (cleanQuery.isBlank()) return SCORE_EXACT_MATCH

        val queryTokens = MULTI_SPACE_REGEX.split(cleanQuery).filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return SCORE_EXACT_MATCH

        // Extraer todos los tokens normalizados del grupo (nombre display + nombres de items)
        val productNames = (listOf(group.displayName) + group.items.map { it.name })
            .map { sanitize(it) }

        val allTokens = productNames
            .flatMap { MULTI_SPACE_REGEX.split(it) }
            .filter { it.isNotBlank() }
            .toSet()

        var totalScore = 0

        for (rawQToken in queryTokens) {
            val qToken = TYPO_MAP[rawQToken] ?: rawQToken
            var bestTokenScore = SCORE_NONE

            // 1. Coincidencia exacta de token
            if (qToken in allTokens) {
                bestTokenScore = SCORE_EXACT_MATCH
                // Bonus si el nombre empieza con el token
                if (productNames.any { it.startsWith(qToken) }) {
                    bestTokenScore += 2_000
                }
            } else {
                // 2. Coincidencia de prefijo
                val hasPrefix = allTokens.any { it.startsWith(qToken) }
                if (hasPrefix) {
                    bestTokenScore = SCORE_PREFIX_MATCH
                } else {
                    // 3. Coincidencia de substring
                    val hasSubstring = allTokens.any { it.contains(qToken) }
                    if (hasSubstring) {
                        bestTokenScore = SCORE_SUBSTRING_MATCH
                    } else {
                        // 4. Fuzzy match restringido (Levenshtein)
                        val minDistance = allTokens.minOfOrNull { levenshtein(qToken, it) } ?: Int.MAX_VALUE

                        if (qToken.length >= 4 && minDistance == 1) {
                            bestTokenScore = SCORE_FUZZY_MATCH_1
                        } else if (qToken.length >= 6 && minDistance == 2) {
                            bestTokenScore = SCORE_FUZZY_MATCH_2
                        }
                    }
                }
            }

            totalScore += bestTokenScore
        }

        return totalScore
    }

    /**
     * Filtra y ordena los grupos de comparación para el Catálogo Maestro según relevancia estricta.
     * Si existen coincidencias exactas o de prefijo, se descartan los falsos positivos fuzzy.
     * A igual relevancia, se ordenan por disponibilidad y orden alfabético del catálogo (nunca por precio).
     */
    fun filterAndRank(groups: List<ComparisonGroup>, query: String): List<ComparisonGroup> {
        val cleanQuery = sanitize(query).trim()
        if (cleanQuery.isBlank() || groups.isEmpty()) {
            return groups.sortedWith(
                compareByDescending<ComparisonGroup> { it.items.size }
                    .thenBy { it.displayName.lowercase(Locale.ROOT) }
            )
        }

        val scored = groups.map { group ->
            group to calculateScore(group, cleanQuery)
        }

        // Si existen productos con alta relevancia (exacta o prefijo), filtramos los falsos positivos fuzzy
        val maxScore = scored.maxOfOrNull { it.second } ?: 0
        val threshold = if (maxScore >= SCORE_EXACT_MATCH) {
            SCORE_PREFIX_MATCH // Solo toleramos exactos y prefijos si ya hay exactos
        } else if (maxScore >= SCORE_PREFIX_MATCH) {
            SCORE_SUBSTRING_MATCH
        } else {
            SCORE_FUZZY_MATCH_2 // Fallback solo si nada coincide
        }

        val relevantGroups = scored.filter { it.second >= threshold }

        // Ordenar primero por Relevancia DESC y luego por disponibilidad / nombre alfabético ASC (NUNCA por precio)
        return relevantGroups
            .sortedWith(
                compareByDescending<Pair<ComparisonGroup, Int>> { it.second }
                    .thenByDescending { it.first.items.size }
                    .thenBy { it.first.displayName.lowercase(Locale.ROOT) }
            )
            .map { it.first }
    }

    private fun sanitize(text: String): String {
        var s = text.lowercase(Locale.ROOT)
        s = s.replace("p.a.n.", "pan").replace("p.a.n", "pan")
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
        s = DIACRITICS_REGEX.replace(s, "")
        s = PUNCTUATION_REGEX.replace(s, " ")
        return s
    }

    /**
     * Calcula la distancia de Levenshtein clásica entre dos cadenas.
     */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = IntArray(b.length + 1) { it }

        for (i in a.indices) {
            var prev = dp[0]
            dp[0] = i + 1
            for (j in b.indices) {
                val temp = dp[j + 1]
                val cost = if (a[i] == b[j]) 0 else 1
                dp[j + 1] = min(min(dp[j + 1] + 1, dp[j] + 1), prev + cost)
                prev = temp
            }
        }

        return dp[b.length]
    }
}
