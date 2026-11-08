package com.resuelve.preciosve.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparisonGroupTest {

    private fun item(id: String, store: Store, total: Double) = ProductItem(
        id = id,
        store = store,
        name = "Producto $id",
        basePriceRef = total,
        totalPriceRef = total,
        totalPriceVes = 0.0,
        imageUrl = "",
        productUrl = ""
    )

    @Test
    fun `create ordena del mas barato al mas caro`() {
        val group = ComparisonGroup.create(
            key = "harina-1kg",
            displayName = "Harina 1 kg",
            items = listOf(
                item("c", Store.CENTRAL, 3.0),
                item("g", Store.GAMA, 1.5),
                item("f", Store.FORUM, 2.0)
            )
        )
        assertEquals(listOf("g", "f", "c"), group.items.map { it.id })
        assertEquals("g", group.cheapest.id)
        assertEquals("c", group.mostExpensive.id)
        assertEquals(1.5, group.savingsRef, 1e-9)
    }

    @Test
    fun `cheapestOf devuelve el mas barato de la tienda o null`() {
        val group = ComparisonGroup.create(
            "k",
            "n",
            listOf(item("g2", Store.GAMA, 5.0), item("g1", Store.GAMA, 4.0), item("f", Store.FORUM, 4.5))
        )
        assertEquals("g1", group.cheapestOf(Store.GAMA)!!.id)
        assertNull(group.cheapestOf(Store.CENTRAL))
    }

    @Test
    fun `un grupo vacio es invalido`() {
        var failed = false
        try {
            ComparisonGroup("k", "n", emptyList())
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `un grupo desordenado es invalido`() {
        var failed = false
        try {
            ComparisonGroup("k", "n", listOf(item("a", Store.GAMA, 5.0), item("b", Store.FORUM, 1.0)))
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `una clave en blanco es invalida`() {
        var failed = false
        try {
            ComparisonGroup(" ", "n", listOf(item("a", Store.GAMA, 5.0)))
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `withBcvRate recalcula bolivares y conserva el orden`() {
        val group = ComparisonGroup.create(
            "k",
            "n",
            listOf(item("b", Store.FORUM, 2.0), item("a", Store.GAMA, 1.0))
        )
        val converted = group.withBcvRate(BcvRate(100.0, 0L, "test"))
        assertEquals(listOf("a", "b"), converted.items.map { it.id })
        assertEquals(100.0, converted.items[0].totalPriceVes, 0.0)
        assertEquals(200.0, converted.items[1].totalPriceVes, 0.0)
    }

    @Test
    fun `un grupo con un solo producto tiene ahorro cero`() {
        val only = item("a", Store.GAMA, 1.0)
        val group = ComparisonGroup.create("k", "n", listOf(only))
        assertSame(only, group.cheapest)
        assertEquals(0.0, group.savingsRef, 0.0)
    }
}
