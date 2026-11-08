package com.resuelve.preciosve

import android.app.Application
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.resuelve.preciosve.di.AppContainer

/**
 * Punto de entrada de la aplicación.
 *
 * Configura Coil globalmente optimizado para dispositivos de gama baja:
 * - Bitmap.Config.RGB_565 (consume exactamente la mitad de memoria RAM que ARGB_8888).
 * - Caché de memoria acotada al 15% de la RAM disponible.
 * - Caché en disco limitada a 50 MB en el directorio de caché de la app.
 * - crossfade(false) para ahorrar ciclos de renderizado y CPU/GPU en teléfonos antiguos.
 * - allowRgb565(true) y reutilización estricta de bitmaps.
 */
class PreciosApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { container.httpClient }
            .bitmapConfig(Bitmap.Config.RGB_565)
            .allowRgb565(true)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024L * 1024L) // 50 MB
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
