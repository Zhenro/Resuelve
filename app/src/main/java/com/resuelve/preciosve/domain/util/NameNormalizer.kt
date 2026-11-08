package com.resuelve.preciosve.domain.util

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizador de nombres y unidades de productos.
 *
 * Agrupa productos equivalentes entre supermercados (ej: "HARINA PAN INTEGRAL 1KG",
 * "Harina  P.A.N. Integral 1 Kg", "Harina Pan Integral 1000 Gr") bajo una clave
 * canónica determinista.
 */
object NameNormalizer {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val PUNCTUATION_REGEX = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    // Stop words que no aportan al agrupamiento de equivalencia
    private val STOP_WORDS = setOf(
        "de", "del", "la", "el", "los", "las", "en", "para", "con", "sin", "d", "y", "e",
        "paquete", "paquetes", "paq", "pqte", "pack", "unidad", "unidades", "unid", "und", "pieza", "pza", "display"
    )

    // Diccionario de equivalencias de unidades y marcas
    private val UNIT_EQUIVALENCES = listOf(
        Regex("(\\d+)\\s*(?:kilos?|kilogramos?|kg)\\b") to "$1kg",
        Regex("(\\d+)\\s*(?:gramos?|gr|g)\\b") to "$1g",
        Regex("(\\d+)\\s*(?:litros?|lts?|l)\\b") to "$1l",
        Regex("(\\d+)\\s*(?:mililitros?|ml|cc)\\b") to "$1ml"
    )

    // Normalizaciones semánticas de términos clave
    private val PHRASE_REPLACEMENTS = listOf(
        Regex("\\b(?:s/\\s*gluten|sin\\s+gluten|gluten\\s*free|libre\\s+de\\s+gluten)\\b") to "singluten",
        Regex("\\b(?:s/\\s*azucar|sin\\s+azucar|sugar\\s*free|libre\\s+de\\s+azucar)\\b") to "sinazucar",
        Regex("\\b(?:extra\\s+suave|x\\s+suave)\\b") to "extrasuave",
        Regex("\\bp\\.a\\.n\\.?\\b") to "pan",
        Regex("\\bc/u\\b") to " "
    )

    /**
     * Normaliza un nombre convirtiéndolo a tokens canónicos ordenados alfabéticamente.
     * Ejemplo: "HARINA DE MAÍZ BLANCO P.A.N. 1KG" -> "1kg blanco harina maiz pan"
     */
    fun canonicalKey(rawName: String?): String {
        if (rawName.isNullOrBlank()) return ""

        var text = rawName.lowercase(Locale.ROOT)

        // Quitar acentos / diacríticos
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
        text = DIACRITICS_REGEX.replace(text, "")

        // Normalizaciones de términos y sinónimos antes de quitar puntuación
        for ((pattern, replacement) in PHRASE_REPLACEMENTS) {
            text = pattern.replace(text, replacement)
        }

        // Normalizar unidades (ej: "1 kg" -> "1kg", "900 gr" -> "900g")
        for ((pattern, replacement) in UNIT_EQUIVALENCES) {
            text = pattern.replace(text, replacement)
        }

        // Convertir 1000g a 1kg para equivalencia de unidades
        text = text.replace(Regex("\\b1000g\\b"), "1kg")
            .replace(Regex("\\b1000ml\\b"), "1l")

        // Quitar puntuaciones
        text = PUNCTUATION_REGEX.replace(text, " ")

        // Separar tokens y filtrar stop words y letras sueltas
        val tokens = MULTI_SPACE_REGEX.split(text)
            .map { it.trim() }
            .filter { token ->
                token.isNotEmpty() &&
                token !in STOP_WORDS &&
                (token.length > 1 || token.all { it.isDigit() })
            }
            .sorted() // Orden determinista independiente del orden de las palabras

        return tokens.joinToString(" ")
    }

    /**
     * Limpia el nombre para visualización legible y elegante en UI.
     */
    fun cleanDisplayName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return ""
        val unescaped = rawName.replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return unescaped.split(" ")
            .joinToString(" ") { word ->
                if (word.length <= 2) word.lowercase(Locale.ROOT)
                else word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }
}
