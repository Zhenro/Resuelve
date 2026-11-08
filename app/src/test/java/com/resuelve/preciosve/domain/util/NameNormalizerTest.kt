package com.resuelve.preciosve.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameNormalizerTest {

    @Test
    fun `normaliza tildes signos y mayusculas`() {
        val key1 = NameNormalizer.canonicalKey("HARINA DE MAÍZ BLANCO P.A.N. 1KG")
        val key2 = NameNormalizer.canonicalKey("Harina  p.a.n.   maiz blanco 1 kg")
        assertEquals(key1, key2)
        assertEquals("1kg blanco harina maiz pan", key1)
    }

    @Test
    fun `convierte unidades gramos a kilos cuando son equivalentes`() {
        val key1 = NameNormalizer.canonicalKey("Harina Pan 1000 Gr")
        val key2 = NameNormalizer.canonicalKey("Harina Pan 1 Kg")
        assertEquals(key1, key2)
    }

    @Test
    fun `elimina stop words del espanol`() {
        val key = NameNormalizer.canonicalKey("Harina de trigo con leudante para tortas")
        // 'de', 'con', 'para' deben ser ignorados
        assertTrue(!key.contains(" de "))
        assertTrue(!key.contains(" con "))
        assertTrue(!key.contains(" para "))
    }

    @Test
    fun `cleanDisplayName da formato titulo y remueve tags html`() {
        val cleaned = NameNormalizer.cleanDisplayName("<em class=\"highlight\">harina</em> pan integral 1kg")
        assertEquals("Harina Pan Integral 1kg", cleaned)
    }

    @Test
    fun `unifica sinonimos de sin gluten y gluten free con formatos distintos`() {
        val key1 = NameNormalizer.canonicalKey("Harina Pan 1Kg de Maiz Blanco S/Gluten")
        val key2 = NameNormalizer.canonicalKey("HARINA de MAIZ BLANCO GLUTEN FREE P.A.N 1 kg")
        assertEquals(key1, key2)
        assertEquals("1kg blanco harina maiz pan singluten", key1)
    }
}
