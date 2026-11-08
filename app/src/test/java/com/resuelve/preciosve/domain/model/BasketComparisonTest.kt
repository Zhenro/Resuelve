package com.resuelve.preciosve.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BasketComparisonTest {

    @Test
    fun `calcula totales y tienda ganadora con ahorro correctamente`() {
        val rate = BcvRate(vesPerRef = 50.0, fetchedAtMillis = System.currentTimeMillis(), source = "TEST")

        val item1 = ShoppingItem(
            id = "harina-pan",
            displayName = "Harina Pan 1kg",
            imageUrl = "",
            quantity = 2,
            gamaPriceRef = 1.20,
            forumPriceRef = 1.00,
            centralPriceRef = 1.10,
            gamaUrl = "",
            forumUrl = "",
            centralUrl = "",
            addedAtMillis = 1000L
        )

        val item2 = ShoppingItem(
            id = "leche-completa",
            displayName = "Leche Completa 1L",
            imageUrl = "",
            quantity = 1,
            gamaPriceRef = 2.00,
            forumPriceRef = 1.80,
            centralPriceRef = 2.10,
            gamaUrl = "",
            forumUrl = "",
            centralUrl = "",
            addedAtMillis = 2000L
        )

        val comparison = BasketComparison.compute(listOf(item1, item2), rate)

        assertEquals(2, comparison.totalDistinctProducts)
        assertEquals(3, comparison.totalUnitsCount) // 2 de harina + 1 de leche

        // Gama: 2 * 1.20 + 1 * 2.00 = 4.40
        assertEquals(4.40, comparison.gamaTotalRef, 0.001)
        // Forum: 2 * 1.00 + 1 * 1.80 = 3.80
        assertEquals(3.80, comparison.forumTotalRef, 0.001)
        // Central: 2 * 1.10 + 1 * 2.10 = 4.30
        assertEquals(4.30, comparison.centralTotalRef, 0.001)

        // Tienda ganadora: Forum (3.80)
        assertEquals(Store.FORUM, comparison.winnerStore)

        // Ahorro max frente a Gama (4.40 - 3.80 = 0.60 Ref.)
        assertEquals(0.60, comparison.maxSavingsRef, 0.001)
        // En Bs. (0.60 * 50 = 30.00 Bs.)
        assertEquals(30.00, comparison.maxSavingsVes, 0.001)
    }

    @Test
    fun `canasta vacia retorna empty()`() {
        val empty = BasketComparison.compute(emptyList(), null)
        assertEquals(true, empty.isEmpty)
        assertEquals(0, empty.totalDistinctProducts)
    }
}
