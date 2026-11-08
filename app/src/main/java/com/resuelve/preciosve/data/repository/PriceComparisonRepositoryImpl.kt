package com.resuelve.preciosve.data.repository

import com.resuelve.preciosve.data.bcv.BcvRateManager
import com.resuelve.preciosve.data.local.CachedProductEntity
import com.resuelve.preciosve.data.local.RecentSearchEntity
import com.resuelve.preciosve.data.local.SearchDao
import com.resuelve.preciosve.domain.model.ComparisonGroup
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.repository.PriceComparisonRepository
import com.resuelve.preciosve.domain.scraper.ScraperResult
import com.resuelve.preciosve.domain.scraper.SupermarketScraper
import com.resuelve.preciosve.domain.util.ProductEntityResolver
import com.resuelve.preciosve.domain.util.RelevanceScorer
import com.resuelve.preciosve.domain.util.VenezuelanBarcodeRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient

/**
 * Implementación de alto rendimiento de [PriceComparisonRepository] con:
 * - Patrón Stale-While-Revalidate (emisión instantánea desde SQLite y actualización en vivo en background).
 * - Historial persistente de búsquedas recientes.
 * - Resolución automática de códigos de barra venezolanos.
 * - Expansión semántica y corrección fonética de consultas.
 * - Autocompletado predictivo.
 */
class PriceComparisonRepositoryImpl(
    private val scrapers: List<SupermarketScraper>,
    private val searchDao: SearchDao,
    private val bcvRateManager: BcvRateManager,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val httpClient: OkHttpClient? = null
) : PriceComparisonRepository {

    override fun search(query: String): Flow<List<ComparisonGroup>> = flow {
        var rawQuery = query.trim().lowercase()
        if (rawQuery.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val now = clock()

        // 1. Resolución de Códigos de Barra
        if (VenezuelanBarcodeRegistry.isBarcode(rawQuery)) {
            var resolvedName = VenezuelanBarcodeRegistry.resolveBarcodeLocal(rawQuery)
            if (resolvedName == null && httpClient != null) {
                resolvedName = VenezuelanBarcodeRegistry.resolveBarcodeOnline(rawQuery, httpClient)
            }
            if (resolvedName != null) {
                rawQuery = resolvedName.lowercase()
            }
        }

        // 2. Expansión semántica de intenciones (ej. "desayuno" -> "harina pan")
        val expandedTerms = ProductEntityResolver.expandSemanticQuery(rawQuery)
        val cleanQuery = expandedTerms.firstOrNull() ?: rawQuery

        // Registrar en historial de búsquedas recientes
        try {
            searchDao.insertRecentSearch(RecentSearchEntity(rawQuery, now))
        } catch (_: Exception) {}

        val currentRate = bcvRateManager.rate.value

        // 3. Emisión instantánea desde caché local (Stale-While-Revalidate)
        // Busca coincidencias directas o por nombre para mostrar resultados en < 5ms
        val immediateCached = try {
            val byQuery = searchDao.getProductsForQuery(cleanQuery)
            if (byQuery.isNotEmpty()) {
                byQuery
            } else {
                searchDao.searchCachedProducts(cleanQuery)
            }
        } catch (_: Exception) {
            emptyList()
        }

        var hasEmittedCache = false
        if (immediateCached.isNotEmpty()) {
            val cachedGroups = buildComparisonGroups(immediateCached.map { it.toDomain() }, currentRate, cleanQuery)
            if (cachedGroups.isNotEmpty()) {
                emit(cachedGroups)
                hasEmittedCache = true
            }
        }

        // Si la consulta en caché aún está fresca y tiene menos de 10 min, evitamos re-scrapear innecesariamente
        val cachedQuery = searchDao.getQuery(cleanQuery)
        if (cachedQuery != null && cachedQuery.expiresAtMillis > now && hasEmittedCache) {
            return@flow
        }

        // Limpiar registros expirados
        try {
            searchDao.deleteExpiredQueries(now)
        } catch (_: Exception) {}

        // 4. Consulta concurrente en vivo a los supermercados (Gama, Forum, Central)
        val allCollectedProducts = mutableListOf<ProductItem>()
        // Si teníamos caché previa, sumarla como base mientras llegan los datos en vivo
        if (immediateCached.isNotEmpty()) {
            allCollectedProducts.addAll(immediateCached.map { it.toDomain() })
        }

        val resultChannel = Channel<ScraperResult>(capacity = scrapers.size)

        supervisorScope {
            launch(ioDispatcher) {
                bcvRateManager.refresh()
            }

            for (scraper in scrapers) {
                launch(ioDispatcher) {
                    val result = scraper.search(cleanQuery)
                    resultChannel.send(result)
                }
            }

            var completedCount = 0
            while (completedCount < scrapers.size) {
                val result = resultChannel.receive()
                completedCount++

                if (result is ScraperResult.Success && result.items.isNotEmpty()) {
                    // Reemplazar productos anteriores de esta tienda con la versión fresca
                    val store = result.items.first().store
                    allCollectedProducts.removeAll { it.store == store }
                    allCollectedProducts.addAll(result.items)

                    val updatedRate = bcvRateManager.rate.value
                    val progressiveGroups = buildComparisonGroups(allCollectedProducts, updatedRate, cleanQuery)
                    emit(progressiveGroups)
                }
            }

            if (allCollectedProducts.isEmpty() && !hasEmittedCache) {
                emit(emptyList())
            }
        }

        // 5. Persistir en caché local
        if (allCollectedProducts.isNotEmpty()) {
            val entities = allCollectedProducts.map {
                CachedProductEntity.fromDomain(it, cleanQuery)
            }
            try {
                searchDao.saveSearchResults(
                    query = cleanQuery,
                    createdAtMillis = now,
                    expiresAtMillis = now + ttlMillis,
                    products = entities
                )
            } catch (_: Exception) {}
        }
    }.flowOn(ioDispatcher)

    override fun observeRecentSearches(): Flow<List<String>> =
        searchDao.observeRecentSearches(10).flowOn(ioDispatcher)

    override suspend fun deleteRecentSearch(query: String) {
        searchDao.deleteRecentSearch(query)
    }

    override suspend fun clearRecentSearches() {
        searchDao.clearRecentSearches()
    }

    override suspend fun getAutocompleteSuggestions(prefix: String): List<String> {
        val clean = prefix.trim().lowercase()
        if (clean.length < 2) return emptyList()

        val results = mutableSetOf<String>()

        // 1. Coincidencia con marcas venezolanas conocidas
        ProductEntityResolver.KNOWN_BRANDS
            .filter { it.startsWith(clean) }
            .take(4)
            .forEach { results.add(it.replaceFirstChar { c -> c.uppercase() }) }

        // 2. Coincidencia con productos en catálogo local
        try {
            val dbSuggestions = searchDao.getAutocompleteSuggestions(clean)
            for (title in dbSuggestions) {
                if (results.size >= 8) break
                val shortName = title.take(40)
                results.add(shortName)
            }
        } catch (_: Exception) {}

        return results.toList()
    }

    private fun buildComparisonGroups(
        products: List<ProductItem>,
        rate: com.resuelve.preciosve.domain.model.BcvRate?,
        query: String
    ): List<ComparisonGroup> {
        val clusters = ProductEntityResolver.clusterProducts(products, rate)
        return RelevanceScorer.filterAndRank(clusters, query)
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 10L * 60L * 1000L // 10 minutos
    }
}
