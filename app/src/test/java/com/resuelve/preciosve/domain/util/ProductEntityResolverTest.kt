package com.resuelve.preciosve.domain.util

import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductEntityResolverTest {

    @Test
    fun `resuelve y fusiona galletas charmy entre diferentes comercios con nombres variantes`() {
        // Forum: "Galletas Charmy Chocolate 192G"
        val itemForum = ProductItem(
            id = "FORUM-123",
            store = Store.FORUM,
            name = "Galletas Charmy Chocolate 192G",
            basePriceRef = 1.48,
            totalPriceRef = 1.48,
            totalPriceVes = 0.0,
            imageUrl = "https://forum/charmy.jpg",
            productUrl = "https://forum/charmy"
        )

        // Gama: "Galleta Puig Charmy Choc 192gr"
        val itemGama = ProductItem(
            id = "GAMA-456",
            store = Store.GAMA,
            name = "Galleta Puig Charmy Choc 192gr",
            basePriceRef = 1.55,
            totalPriceRef = 1.55,
            totalPriceVes = 0.0,
            imageUrl = "https://gama/charmy.jpg",
            productUrl = "https://gama/charmy"
        )

        val normForum = ProductEntityResolver.normalize(itemForum)
        val normGama = ProductEntityResolver.normalize(itemGama)

        assertTrue(ProductEntityResolver.areSameEntity(normForum, normGama))

        val clusters = ProductEntityResolver.clusterProducts(listOf(itemForum, itemGama), null)

        // Deben agruparse bajo 1 sola tarjeta maestra
        assertEquals(1, clusters.size)
        val group = clusters.first()

        // Contiene ambas tiendas ordenadas de menor a mayor precio
        assertEquals(2, group.items.size)
        assertEquals(Store.FORUM, group.items[0].store)
        assertEquals(1.48, group.items[0].totalPriceRef, 0.001)
        assertEquals(Store.GAMA, group.items[1].store)
        assertEquals(1.55, group.items[1].totalPriceRef, 0.001)
    }

    @Test
    fun `resuelve y fusiona yogurt yoka dulce y natural de 150g bajo una sola ficha maestra`() {
        // Caso real reportado por el usuario:
        // Gama: "Yogurt Yoka 150G Firme Dulce"
        val itemGama = ProductItem(
            id = "GAMA-YOKA-1",
            store = Store.GAMA,
            name = "Yogurt Yoka 150G Firme Dulce",
            basePriceRef = 1.98,
            totalPriceRef = 1.98,
            totalPriceVes = 0.0,
            imageUrl = "https://gama/yoka_natural.jpg",
            productUrl = "https://gama/yoka"
        )

        // Forum: "Yogurt Yoka Firme Natural 150Gr."
        val itemForum = ProductItem(
            id = "FORUM-YOKA-1",
            store = Store.FORUM,
            name = "Yogurt Yoka Firme Natural 150Gr.",
            basePriceRef = 2.85,
            totalPriceRef = 2.85,
            totalPriceVes = 0.0,
            imageUrl = "https://forum/yoka_natural.jpg",
            productUrl = "https://forum/yoka"
        )

        // Central: "Yogurt Yoka Firme Natural 150Gr."
        val itemCentral = ProductItem(
            id = "CENTRAL-YOKA-1",
            store = Store.CENTRAL,
            name = "Yogurt Yoka Firme Natural 150Gr.",
            basePriceRef = 2.82,
            totalPriceRef = 2.82,
            totalPriceVes = 0.0,
            imageUrl = "https://central/yoka_natural.jpg",
            productUrl = "https://central/yoka"
        )

        val normGama = ProductEntityResolver.normalize(itemGama)
        val normForum = ProductEntityResolver.normalize(itemForum)
        val normCentral = ProductEntityResolver.normalize(itemCentral)

        // Verificamos canonical slugs idénticos a pesar de variaciones "150G Firme Dulce" vs "Firme Natural 150Gr."
        assertEquals(normForum.canonicalSlug, normGama.canonicalSlug)
        assertEquals(normForum.canonicalSlug, normCentral.canonicalSlug)

        assertTrue(ProductEntityResolver.areSameEntity(normGama, normForum))
        assertTrue(ProductEntityResolver.areSameEntity(normForum, normCentral))

        val clusters = ProductEntityResolver.clusterProducts(listOf(itemGama, itemForum, itemCentral), null)

        // Deben agruparse en 1 único producto maestro con las 3 ofertas
        assertEquals(1, clusters.size)
        val masterGroup = clusters.first()
        assertEquals(3, masterGroup.items.size)
        assertTrue(masterGroup.items.any { it.store == Store.GAMA })
        assertTrue(masterGroup.items.any { it.store == Store.FORUM })
        assertTrue(masterGroup.items.any { it.store == Store.CENTRAL })
    }

    @Test
    fun `no fusiona yogurt natural con yogurt de durazno`() {
        val yokaNatural = ProductItem("1", Store.FORUM, "Yogurt Yoka Firme Natural 150Gr.", 2.85, 2.85, 0.0, "", "")
        val yokaDurazno = ProductItem("2", Store.FORUM, "Yogurt Yoka Firme Durazno 150Gr", 2.29, 2.29, 0.0, "", "")

        val normNat = ProductEntityResolver.normalize(yokaNatural)
        val normDur = ProductEntityResolver.normalize(yokaDurazno)

        assertFalse(ProductEntityResolver.areSameEntity(normNat, normDur))

        val clusters = ProductEntityResolver.clusterProducts(listOf(yokaNatural, yokaDurazno), null)
        assertEquals(2, clusters.size)
    }

    @Test
    fun `filtra imagenes cruzadas incompatibles como foto de arroz mary en producto oreo`() {
        // Caso real: Central Madeirense devuelve imagen con 'mary' para Galleta Oreo
        val oreoCorrupted = ProductItem(
            id = "CENTRAL-OREO-1",
            store = Store.CENTRAL,
            name = "Galleta Nabisco Oreo Tipo Americano 48Gr",
            basePriceRef = 0.77,
            totalPriceRef = 0.77,
            totalPriceVes = 0.0,
            imageUrl = "https://tucentralonline.com/uploads/arroz-mary-1kg.jpg",
            productUrl = "https://central/oreo"
        )

        // Forum tiene la imagen auténtica
        val oreoValid = ProductItem(
            id = "FORUM-OREO-1",
            store = Store.FORUM,
            name = "Galleta Nabisco Oreo Tipo Americano 48Gr",
            basePriceRef = 0.85,
            totalPriceRef = 0.85,
            totalPriceVes = 0.0,
            imageUrl = "https://forum/oreo-americano-48g.png",
            productUrl = "https://forum/oreo"
        )

        assertFalse(ProductEntityResolver.isImageValidForProduct(oreoCorrupted.imageUrl, "oreo"))
        assertTrue(ProductEntityResolver.isImageValidForProduct(oreoValid.imageUrl, "oreo"))

        val clusters = ProductEntityResolver.clusterProducts(listOf(oreoCorrupted, oreoValid), null)
        assertEquals(1, clusters.size)

        // El producto maestro debe tomar la imagen válida de Forum y NO la foto de Arroz Mary
        val masterItem = clusters.first()
        val representativeImage = masterItem.items.first().imageUrl
        assertEquals("https://forum/oreo-americano-48g.png", representativeImage)
    }

    @Test
    fun `no fusiona productos con gramajes claramente diferentes`() {
        val item1 = ProductItem("1", Store.FORUM, "Galletas Charmy Chocolate 192G", 1.48, 1.48, 0.0, "", "")
        val item2 = ProductItem("2", Store.GAMA, "Galletas Charmy Chocolate 500G", 3.20, 3.20, 0.0, "", "")

        val norm1 = ProductEntityResolver.normalize(item1)
        val norm2 = ProductEntityResolver.normalize(item2)

        assertFalse(ProductEntityResolver.areSameEntity(norm1, norm2))

        val clusters = ProductEntityResolver.clusterProducts(listOf(item1, item2), null)
        assertEquals(2, clusters.size)
    }
}
