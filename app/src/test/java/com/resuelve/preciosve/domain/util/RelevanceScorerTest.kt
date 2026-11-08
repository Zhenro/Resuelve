package com.resuelve.preciosve.domain.util

import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelevanceScorerTest {

    @Test
    fun `exact match de charmy supera a cherry halls a pesar de que cherry sea mas barato`() {
        // Falso positivo barato devuelto por scraping: Halls Cherry ($0.65)
        val hallsCherry = ComparisonGroup.create(
            key = "caramelos cherry lyptus halls 25.2g",
            displayName = "CARAMELOS CHERRY LYPTUS HALLS 9s 25.2G",
            items = listOf(ProductItem("1", Store.GAMA, "CARAMELOS CHERRY LYPTUS HALLS 9s 25.2G", 0.65, 0.65, 0.0, "", ""))
        )

        // Producto exacto buscado: Charmy Chocolate ($1.48)
        val charmyChoc = ComparisonGroup.create(
            key = "galletas charmy chocolate 192g",
            displayName = "Galletas Charmy Chocolate 192G",
            items = listOf(ProductItem("2", Store.FORUM, "Galletas Charmy Chocolate 192G", 1.48, 1.48, 0.0, "", ""))
        )

        val groups = listOf(hallsCherry, charmyChoc)
        val ranked = RelevanceScorer.filterAndRank(groups, "charmy")

        // Charmy debe estar en la posición #1 indiscutiblemente
        assertEquals("Galletas Charmy Chocolate 192G", ranked.first().displayName)

        // Score exacto de Charmy debe ser mucho mayor que el de Cherry
        val scoreCharmy = RelevanceScorer.calculateScore(charmyChoc, "charmy")
        val scoreCherry = RelevanceScorer.calculateScore(hallsCherry, "charmy")
        assertTrue(scoreCharmy >= RelevanceScorer.SCORE_EXACT_MATCH)
        assertTrue(scoreCherry < RelevanceScorer.SCORE_SUBSTRING_MATCH)
    }

    @Test
    fun `a igual relevancia ordena por catalogo alfabetico y disponibilidad nunca por precio`() {
        val charmyVainilla = ComparisonGroup.create(
            key = "galletas charmy vainilla 192g",
            displayName = "Galletas Charmy Vainilla 192G",
            items = listOf(ProductItem("1", Store.FORUM, "Galletas Charmy Vainilla 192G", 1.48, 1.48, 0.0, "", ""))
        )

        val charmyChoc = ComparisonGroup.create(
            key = "galletas charmy chocolate 192g",
            displayName = "Galletas Charmy Chocolate 192G",
            items = listOf(ProductItem("2", Store.FORUM, "Galletas Charmy Chocolate 192G", 1.60, 1.60, 0.0, "", ""))
        )

        val ranked = RelevanceScorer.filterAndRank(listOf(charmyVainilla, charmyChoc), "charmy")

        // Ambos son exact match, pero Charmy Chocolate va antes alfabéticamente que Charmy Vainilla (a pesar de costar $1.60 vs $1.48)
        assertEquals("Galletas Charmy Chocolate 192G", ranked[0].displayName)
        assertEquals("Galletas Charmy Vainilla 192G", ranked[1].displayName)
    }
}
