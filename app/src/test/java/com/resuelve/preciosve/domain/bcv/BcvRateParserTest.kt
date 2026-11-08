package com.resuelve.preciosve.domain.bcv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BcvRateParserTest {

    @Test
    fun `parsea el banner estandar de Gama`() {
        assertEquals(880.12, BcvRateParser.parseBanner("BCV | Ref. 1 = Bs. 880,12")!!, 1e-9)
    }

    @Test
    fun `parsea con miles y espacios duros`() {
        assertEquals(
            1234.56,
            BcvRateParser.parseBanner("BCV\u00A0|\u00A0Ref. 1 = Bs.\u00A01.234,56")!!,
            1e-9
        )
    }

    @Test
    fun `parsea dentro de un texto mas largo y sin distinguir mayusculas`() {
        val text = "Bienvenido  bcv | ref. 1 = bs. 90,5  Gama en línea"
        assertEquals(90.5, BcvRateParser.parseBanner(text)!!, 1e-9)
    }

    @Test
    fun `devuelve null si no hay banner`() {
        assertNull(BcvRateParser.parseBanner("Ref. 5,00"))
        assertNull(BcvRateParser.parseBanner(""))
        assertNull(BcvRateParser.parseBanner(null))
    }

    @Test
    fun `devuelve null si la tasa es cero`() {
        assertNull(BcvRateParser.parseBanner("BCV | Ref. 1 = Bs. 0,00"))
    }
}
