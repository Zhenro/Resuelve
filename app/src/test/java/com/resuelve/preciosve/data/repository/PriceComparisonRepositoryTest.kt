package com.resuelve.preciosve.data.repository

import com.resuelve.preciosve.data.bcv.BcvRateManager
import com.resuelve.preciosve.data.local.CachedProductEntity
import com.resuelve.preciosve.data.local.SearchDao
import com.resuelve.preciosve.data.local.SearchQueryEntity
import com.resuelve.preciosve.domain.bcv.RateStore
import com.resuelve.preciosve.domain.model.BcvRate
import com.resuelve.preciosve.domain.model.ProductItem
import com.resuelve.preciosve.domain.model.Store
import com.resuelve.preciosve.domain.scraper.ScraperResult
import com.resuelve.preciosve.domain.scraper.SupermarketScraper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PriceComparisonRepositoryTest {

    private class FakeDao : SearchDao {
        var queryEntity: SearchQueryEntity? = null
        val products = mutableListOf<CachedProductEntity>()
        var savedQueries = 0

        override suspend fun getQuery(query: String): SearchQueryEntity? =
            if (queryEntity?.query == query) queryEntity else null

        override suspend fun getProductsForQuery(query: String): List<CachedProductEntity> =
            products.filter { it.searchQuery == query }

        override suspend fun insertQuery(queryEntity: SearchQueryEntity) {
            this.queryEntity = queryEntity
        }

        override suspend fun insertProducts(products: List<CachedProductEntity>) {
            this.products.addAll(products)
        }

        override suspend fun deleteQuery(query: String) {
            if (queryEntity?.query == query) queryEntity = null
            products.removeAll { it.searchQuery == query }
        }

        override suspend fun deleteExpiredQueries(nowMillis: Long) {
            if (queryEntity != null && queryEntity!!.expiresAtMillis < nowMillis) {
                queryEntity = null
                products.clear()
            }
        }

        override suspend fun searchCachedProducts(query: String): List<CachedProductEntity> =
            products.filter { it.name.contains(query, ignoreCase = true) }

        override suspend fun getAutocompleteSuggestions(prefix: String): List<String> =
            products.map { it.name }.filter { it.startsWith(prefix, ignoreCase = true) }

        override fun observeRecentSearches(limit: Int): kotlinx.coroutines.flow.Flow<List<String>> =
            kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun insertRecentSearch(recentSearch: com.resuelve.preciosve.data.local.RecentSearchEntity) {}

        override suspend fun deleteRecentSearch(query: String) {}

        override suspend fun clearRecentSearches() {}

        override suspend fun saveSearchResults(
            query: String,
            createdAtMillis: Long,
            expiresAtMillis: Long,
            products: List<CachedProductEntity>
        ) {
            savedQueries++
            queryEntity = SearchQueryEntity(query, createdAtMillis, expiresAtMillis)
            this.products.removeAll { it.searchQuery == query }
            this.products.addAll(products)
        }
    }

    private class FakeScraper(
        override val store: Store,
        private val delayMs: Long = 0L,
        private val items: List<ProductItem>
    ) : SupermarketScraper {
        var called = false

        override suspend fun search(query: String): ScraperResult {
            called = true
            if (delayMs > 0) delay(delayMs)
            return ScraperResult.Success(store, items, delayMs)
        }
    }

    private fun item(id: String, store: Store, name: String, price: Double) = ProductItem(
        id = id,
        store = store,
        name = name,
        basePriceRef = price,
        totalPriceRef = price,
        totalPriceVes = 0.0,
        imageUrl = "",
        productUrl = ""
    )

    @Test
    fun `emite inmediatamente desde cache cuando el TTL sigue vigente`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val now = 1000L
        val dao = FakeDao().apply {
            queryEntity = SearchQueryEntity("harina", createdAtMillis = now, expiresAtMillis = now + 600_000L)
            products.add(
                CachedProductEntity(
                    id = "GAMA-1",
                    searchQuery = "harina",
                    store = Store.GAMA.name,
                    name = "Harina Pan 1kg",
                    basePriceRef = 1.0,
                    totalPriceRef = 1.0,
                    totalPriceVes = 0.0,
                    imageUrl = "",
                    productUrl = ""
                )
            )
        }

        val scraper = FakeScraper(Store.GAMA, items = emptyList())
        val bcvManager = BcvRateManager(emptyList(), object : RateStore {
            override fun load(): BcvRate? = null
            override fun save(rate: BcvRate) {}
        })

        val repository = PriceComparisonRepositoryImpl(
            scrapers = listOf(scraper),
            searchDao = dao,
            bcvRateManager = bcvManager,
            clock = { now },
            ioDispatcher = testDispatcher
        )

        val emissions = repository.search("harina").toList()
        assertEquals(1, emissions.size)
        assertEquals(1, emissions[0].size)
        assertEquals("GAMA-1", emissions[0][0].cheapest.id)
        assertTrue("No debió llamar al scraper de red", !scraper.called)
    }

    @Test
    fun `emite progresivamente por cada scraper y persiste en cache si caduco`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val now = 1000L
        val dao = FakeDao()

        val itemGama = item("GAMA-1", Store.GAMA, "Harina Pan 1kg", 1.20)
        val itemForum = item("FORUM-1", Store.FORUM, "Harina Pan 1kg", 0.95)

        val scraperGama = FakeScraper(Store.GAMA, delayMs = 10L, items = listOf(itemGama))
        val scraperForum = FakeScraper(Store.FORUM, delayMs = 50L, items = listOf(itemForum))

        val bcvManager = BcvRateManager(emptyList(), object : RateStore {
            override fun load(): BcvRate? = null
            override fun save(rate: BcvRate) {}
        })

        val repository = PriceComparisonRepositoryImpl(
            scrapers = listOf(scraperGama, scraperForum),
            searchDao = dao,
            bcvRateManager = bcvManager,
            clock = { now },
            ioDispatcher = testDispatcher
        )

        val emissions = repository.search("harina").toList()

        // Emisión 1: Responde Gama primero (1 grupo con 1 item)
        // Emisión 2: Responde Forum después (se agrupan porque tienen la misma clave normalizada)
        assertTrue(emissions.size >= 2)
        val finalGroup = emissions.last()[0]
        assertEquals(2, finalGroup.items.size)
        assertEquals("FORUM-1", finalGroup.cheapest.id) // 0.95 vs 1.20

        // Se guardó en caché
        assertEquals(1, dao.savedQueries)
        assertEquals(2, dao.products.size)
    }
}
