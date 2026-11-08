package com.resuelve.preciosve.data.remote.central

import com.resuelve.preciosve.data.remote.awaitString
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import com.resuelve.preciosve.domain.scraper.ScraperResult
import com.resuelve.preciosve.domain.scraper.SupermarketScraper
import com.resuelve.preciosve.domain.scraper.runScraper
import com.resuelve.preciosve.domain.util.LocalizedNumberParser
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Conector para Central Madeirense (Tu Central Online, Sucursal 50: La Alameda).
 * Realiza Web Scraping SSR usando Jsoup sobre WooCommerce.
 * Extrae título, precio normal o en descuento (ins), imagen y enlace del producto.
 */
class CentralScraper(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL
) : SupermarketScraper {

    override val store: Store = Store.CENTRAL

    override suspend fun search(query: String): ScraperResult = runScraper(store) {
        if (query.isBlank()) return@runScraper emptyList()

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/?s=$encodedQuery&post_type=product"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        val html = client.newCall(request).awaitString()
        parseHtml(html)
    }

    internal fun parseHtml(html: String): List<ProductItem> {
        val doc = Jsoup.parse(html)
        // Seleccionamos las tarjetas de producto del catálogo principal,
        // excluyendo widgets laterales de recomendaciones/más vendidos y carruseles ajenos
        var productElements = doc.select("ul.products > li.product-col, ul.products > li.product, .archive-products ul.products li")
        if (productElements.isEmpty()) {
            productElements = doc.select("#content li.product, .site-main li.product, li.product-col")
        }
        if (productElements.isEmpty()) {
            productElements = doc.select("li.product")
        }

        val results = mutableListOf<ProductItem>()
        val seenIds = mutableSetOf<String>()

        for (el in productElements) {
            // Ignorar elementos dentro de widgets de barra lateral o pie de página o carruseles promocionales
            if (el.parents().any { it.hasClass("widget") || it.hasClass("sidebar") || it.hasClass("product_list_widget") }) {
                continue
            }
            val item = parseProductElement(el) ?: continue
            if (seenIds.add(item.id)) {
                results.add(item)
            }
        }
        return results
    }

    private fun parseProductElement(el: Element): ProductItem? {
        val titleEl = el.selectFirst(".woocommerce-loop-product__title, .product-loop-title, h3") ?: return null
        val title = titleEl.text().trim().takeIf { it.isNotEmpty() } ?: return null

        val linkEl = el.selectFirst("a[href*='/producto/']")
        val productUrl = linkEl?.attr("abs:href")?.ifBlank { linkEl.attr("href") }.orEmpty()

        // El ID estable se extrae de la clase viewcart-XXXXX o del slug del link
        val cartClass = el.selectFirst("[class*='viewcart-']")?.className()
        val idFromCart = cartClass?.let { REGEX_VIEWCART.find(it)?.groupValues?.get(1) }
        val idFromSlug = productUrl.trimEnd('/').substringAfterLast('/')
        val id = idFromCart ?: idFromSlug.takeIf { it.isNotEmpty() } ?: title.hashCode().toString()

        // Precio: si hay oferta (<ins>), se usa el precio dentro de <ins>.
        val priceContainer = el.selectFirst(".price") ?: return null
        val insAmount = priceContainer.selectFirst("ins .woocommerce-Price-amount")
        val delAmount = priceContainer.selectFirst("del .woocommerce-Price-amount")
        val normalAmount = priceContainer.selectFirst(".woocommerce-Price-amount")

        val targetPriceEl = insAmount ?: normalAmount ?: return null
        val rawPriceText = targetPriceEl.text()
        val totalPrice = LocalizedNumberParser.parse(rawPriceText) ?: return null
        if (!totalPrice.isFinite() || totalPrice <= 0.0) return null

        val basePrice = delAmount?.text()?.let { LocalizedNumberParser.parse(it) } ?: totalPrice

        val imgContainer = el.selectFirst(".product-image, .box-image, .image-tools") ?: el
        val imgEl = imgContainer.selectFirst("img.wp-post-image, img.attachment-woocommerce_thumbnail, .inner img")
            ?: imgContainer.selectFirst("img")

        val rawImg = imgEl?.attr("data-src")?.ifBlank { imgEl.attr("src") }
            ?: imgEl?.attr("data-lazy-src")
            ?: imgEl?.attr("src")

        val imageUrl = rawImg
            ?.takeIf { !it.contains("woocommerce-placeholder") && !it.contains("placeholder") && !it.startsWith("data:image") }
            ?.let { sanitizeImageUrl(it, title) }
            .orEmpty()

        return ProductItem(
            id = "CENTRAL-$id",
            store = Store.CENTRAL,
            name = title,
            basePriceRef = basePrice,
            totalPriceRef = totalPrice,
            totalPriceVes = 0.0,
            imageUrl = imageUrl,
            productUrl = productUrl
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://tucentralonline.com/La-Alameda-50"
        private val REGEX_VIEWCART = Regex("viewcart-(\\d+)")

        internal fun sanitizeImageUrl(url: String, productTitle: String): String {
            val lowerTitle = productTitle.lowercase()
            val lowerUrl = url.lowercase()

            // Si el producto es Oreo y la imagen menciona Mary, Arroz, Harina, rechazar
            if (lowerTitle.contains("oreo") && (lowerUrl.contains("mary") || lowerUrl.contains("arroz") || lowerUrl.contains("harina"))) {
                return ""
            }
            if (lowerTitle.contains("yoka") && (lowerUrl.contains("mary") || lowerUrl.contains("arroz") || lowerUrl.contains("harina"))) {
                return ""
            }

            val conflictingBrands = listOf("mary", "pan", "mavesa", "primor", "harina-pan", "arroz-mary")
            for (cb in conflictingBrands) {
                if (lowerUrl.contains(cb) && !lowerTitle.contains(cb.replace("-", " "))) {
                    return ""
                }
            }
            return url
        }
    }
}
