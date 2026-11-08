package com.resuelve.preciosve.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSearchFeaturesTest {

    @Test
    fun `verificar resolucion de codigos de barra venezolanos frecuentes`() {
        // Harina P.A.N.
        val pan = VenezuelanBarcodeRegistry.resolveBarcode("7591008000013")
        assertNotNull(pan)
        assertTrue(pan!!.contains("Harina P.A.N."))

        // Arroz Mary
        val mary = VenezuelanBarcodeRegistry.resolveBarcode("7591016000010")
        assertNotNull(mary)
        assertTrue(mary!!.contains("Arroz Mary"))

        // Mayonesa Mavesa
        val mavesa = VenezuelanBarcodeRegistry.resolveBarcode("7591011000015")
        assertNotNull(mavesa)
        assertTrue(mavesa!!.contains("Mayonesa Mavesa"))

        // Códigos escaneados directamente por el usuario en su despensa
        val clubSocial = VenezuelanBarcodeRegistry.resolveBarcode("7622202324116")
        assertNotNull(clubSocial)
        assertTrue(clubSocial!!.contains("Club Social"))

        val margarina = VenezuelanBarcodeRegistry.resolveBarcode("7590006200137")
        assertNotNull(margarina)
        assertTrue(margarina!!.contains("Mavesa"))

        val cotufas = VenezuelanBarcodeRegistry.resolveBarcode("7591473004976")
        assertNotNull(cotufas)
        assertTrue(cotufas!!.contains("Mary"))

        // Validador de código de barras
        assertTrue(VenezuelanBarcodeRegistry.isBarcode("7591008000013"))
        assertTrue(!VenezuelanBarcodeRegistry.isBarcode("harina pan"))
    }

    @Test
    fun `verificar calculo de precio unitario estandarizado por kg y 100g`() {
        // Paquete de 1kg a Ref. 1.20 -> Ref. 1.20/kg
        val unit1kg = ProductEntityResolver.calculateUnitPrice(1.20, 1000.0)
        assertEquals("Ref. 1.20/kg", unit1kg)

        // Paquete de 500g a Ref. 1.50 -> Ref. 3.00/kg
        val unit500g = ProductEntityResolver.calculateUnitPrice(1.50, 500.0)
        assertEquals("Ref. 3.00/kg", unit500g)

        // Paquete de 150g a Ref. 1.50 -> Ref. 1.00/100g
        val unit150g = ProductEntityResolver.calculateUnitPrice(1.50, 150.0)
        assertEquals("Ref. 1.00/100g", unit150g)
    }

    @Test
    fun `verificar expansion semantica de intenciones cotidianas en Caracas`() {
        val desayuno = ProductEntityResolver.expandSemanticQuery("desayuno")
        assertTrue(desayuno.any { it.contains("harina") })

        val arepa = ProductEntityResolver.expandSemanticQuery("arepa")
        assertTrue(arepa.any { it.contains("harina") })

        val limpieza = ProductEntityResolver.expandSemanticQuery("limpieza")
        assertTrue(limpieza.any { it.contains("clorox") || it.contains("ace") })
    }

    @Test
    fun `verificar tolerancia a errores tipograficos venezolanos`() {
        val item1 = com.resuelve.preciosve.domain.model.ProductItem(
            id = "1",
            store = com.resuelve.preciosve.domain.model.Store.GAMA,
            name = "Harina P.A.N. Maiz Blanco 1kg",
            basePriceRef = 1.0,
            totalPriceRef = 1.0,
            totalPriceVes = 0.0,
            imageUrl = "",
            productUrl = ""
        )
        val group = com.resuelve.preciosve.domain.model.ComparisonGroup.create("pan", "Harina P.A.N. Maiz Blanco 1kg", listOf(item1))

        // Búsqueda con error de tipeo / fonético: "arina pan"
        val score = RelevanceScorer.calculateScore(group, "arina pan")
        // Debe obtener alta puntuación de relevancia
        assertTrue(score > 5000)
    }
}
