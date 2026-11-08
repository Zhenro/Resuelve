package com.resuelve.preciosve.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalizedNumberParserTest {

    private fun assertParses(expected: Double, raw: String?) {
        val actual = LocalizedNumberParser.parse(raw)
        assertEquals("parse('$raw')", expected, actual ?: Double.NaN, 1e-9)
    }

    @Test
    fun `coma decimal simple`() {
        assertParses(880.12, "880,12")
        assertParses(1.49, "1,49")
        assertParses(0.5, "0,5")
    }

    @Test
    fun `miles con punto y decimal con coma`() {
        assertParses(1234.56, "1.234,56")
        assertParses(1234567.89, "1.234.567,89")
    }

    @Test
    fun `formato ingles con coma de miles y punto decimal`() {
        assertParses(1234.56, "1,234.56")
    }

    @Test
    fun `punto decimal con dos decimales`() {
        assertParses(5.19, "Ref. 5.19")
        assertParses(880.1159, "880.1159")
    }

    @Test
    fun `un punto con tres decimales se trata como miles salvo que empiece en cero`() {
        assertParses(1234.0, "1.234")
        assertParses(0.123, "0.123")
    }

    @Test
    fun `extrae el numero dentro de texto con simbolos y espacio duro`() {
        assertParses(1.49, "#\u00A01,49")
        assertParses(2200.29, "Bs. 2.200,29")
    }

    @Test
    fun `ignora puntuacion final`() {
        assertParses(880.12, "880,12.")
    }

    @Test
    fun `entradas invalidas devuelven null`() {
        assertNull(LocalizedNumberParser.parse(null))
        assertNull(LocalizedNumberParser.parse(""))
        assertNull(LocalizedNumberParser.parse("   "))
        assertNull(LocalizedNumberParser.parse("sin numeros"))
    }

    @Test
    fun `enteros sin separadores`() {
        assertParses(42.0, "42")
    }
}
