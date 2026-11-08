package com.resuelve.preciosve.di

import android.content.Context
import com.resuelve.preciosve.data.bcv.BcvRateManager
import com.resuelve.preciosve.data.bcv.DolarApiBcvSource
import com.resuelve.preciosve.data.bcv.SharedPreferencesRateStore
import com.resuelve.preciosve.data.remote.HttpClientProvider
import com.resuelve.preciosve.data.remote.central.CentralScraper
import com.resuelve.preciosve.data.remote.forum.ForumZupperScraper
import com.resuelve.preciosve.data.remote.gama.GamaScraper
import com.resuelve.preciosve.domain.scraper.SupermarketScraper
import com.resuelve.preciosve.data.local.AppDatabase
import com.resuelve.preciosve.data.local.SearchDao
import com.resuelve.preciosve.data.repository.PriceComparisonRepositoryImpl
import com.resuelve.preciosve.domain.repository.PriceComparisonRepository
import okhttp3.OkHttpClient

/**
 * Contenedor de dependencias manual (sin Hilt/Dagger) para minimizar el arranque y el tamaño del APK.
 * Todo se crea de forma perezosa.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Único `OkHttpClient` de la app. */
    val httpClient: OkHttpClient
        get() = HttpClientProvider.client

    /** Scrapers de supermercados */
    val gamaScraper: GamaScraper by lazy {
        GamaScraper(httpClient)
    }

    val forumScraper: ForumZupperScraper by lazy {
        ForumZupperScraper(httpClient)
    }

    val centralScraper: CentralScraper by lazy {
        CentralScraper(httpClient)
    }

    val scrapers: List<SupermarketScraper> by lazy {
        listOf(gamaScraper, forumScraper, centralScraper)
    }

    /** Base de datos Room */
    val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    val searchDao: SearchDao by lazy {
        database.searchDao()
    }

    val shoppingDao: com.resuelve.preciosve.data.local.ShoppingDao by lazy {
        database.shoppingDao()
    }

    /** Repositorio de comparación de precios */
    val priceComparisonRepository: PriceComparisonRepository by lazy {
        PriceComparisonRepositoryImpl(
            scrapers = scrapers,
            searchDao = searchDao,
            bcvRateManager = bcvRateManager,
            httpClient = httpClient
        )
    }

    /** Repositorio de lista de compras y cesta consolidada */
    val shoppingListRepository: com.resuelve.preciosve.domain.repository.ShoppingListRepository by lazy {
        com.resuelve.preciosve.data.repository.ShoppingListRepositoryImpl(
            shoppingDao = shoppingDao,
            bcvRateManager = bcvRateManager
        )
    }

    /** Gestor global de la tasa BCV: Gama -> Forum Zupper -> DolarApi */
    val bcvRateManager: BcvRateManager by lazy {
        BcvRateManager(
            sources = listOf(
                gamaScraper,
                forumScraper,
                DolarApiBcvSource(httpClient)
            ),
            store = SharedPreferencesRateStore(appContext)
        )
    }
}
