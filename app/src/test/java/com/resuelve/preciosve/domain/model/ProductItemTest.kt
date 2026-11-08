package com.resuelve.preciosve.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductItemTest {

    private fun item(total: Double, ves: Double = 0.0) = ProductItem(
        id = "GAMA-1",
        store = Store.GAMA,
        name = "Harina de maíz 1 kg",
        basePriceRef = total,
        totalPriceRef = total,
        totalPriceVes = ves,
        imageUrl = "",
        productUrl = ""
    )

    @Test
    fun `calcula bolivares con redondeo HALF_UP a 2 decimales`() {
        // 2.5 * 880.1159 = 2200.28975 -> 2200.29
        assertEquals(2200.29, ProductItem.calculateVes(2.5, 880.1159), 0.0)
        assertEquals(1761.0, ProductItem.calculateVes(2.0, 880.5), 0.0)
    }

    @Test
    fun `sin tasa valida devuelve cero`() {
        assertEquals(0.0, ProductItem.calculateVes(2.5, null), 0.0)
        assertEquals(0.0, ProductItem.calculateVes(2.5, 0.0), 0.0)
        assertEquals(0.0, ProductItem.calculateVes(2.5, Double.NaN), 0.0)
        assertEquals(0.0, ProductItem.calculateVes(Double.NaN, 100.0), 0.0)
    }

    @Test
    fun `withBcvRate actualiza solo el precio en bolivares`() {
        val rate = BcvRate(vesPerRef = 100.0, fetchedAtMillis = 0L, source = "test")
        val result = item(total = 3.0).withBcvRate(rate)
        assertEquals(300.0, result.totalPriceVes, 0.0)
        assertEquals(3.0, result.totalPriceRef, 0.0)
        assertTrue(result.hasVes)
    }

    @Test
    fun `withBcvRate nulo limpia los bolivares`() {
        val result = item(total = 3.0, ves = 300.0).withBcvRate(null)
        assertEquals(0.0, result.totalPriceVes, 0.0)
        assertFalse(result.hasVes)
    }

    @Test
    fun `isComparable exige precio finito y positivo`() {
        assertTrue(item(1.0).isComparable)
        assertFalse(item(0.0).isComparable)
        assertFalse(item(-1.0).isComparable)
        assertFalse(item(Double.NaN).isComparable)
        assertFalse(item(Double.POSITIVE_INFINITY).isComparable)
    }

    @Test
    fun `PRICE_ORDER ordena por precio y desempata por tienda`() {
        val central = item(2.0).copy(id = "C", store = Store.CENTRAL)
        val gama = item(2.0).copy(id = "G", store = Store.GAMA)
        val cheap = item(1.0).copy(id = "F", store = Store.FORUM)
        val sorted = listOf(central, gama, cheap).sortedWith(ProductItem.PRICE_ORDER)
        assertEquals(listOf("F", "G", "C"), sorted.map { it.id })
    }

    @Test
    fun `BcvRate rechaza tasas invalidas y calcula frescura`() {
        val rate = BcvRate(10.0, fetchedAtMillis = 1_000L, source = "test")
        assertTrue(rate.isFresh(nowMillis = 1_500L, ttlMillis = 1_000L))
        assertFalse(rate.isFresh(nowMillis = 2_500L, ttlMillis = 1_000L))
        assertFalse(rate.isFresh(nowMillis = 500L, ttlMillis = 1_000L))
        var failed = false
        try {
            BcvRate(0.0, 0L, "x")
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
