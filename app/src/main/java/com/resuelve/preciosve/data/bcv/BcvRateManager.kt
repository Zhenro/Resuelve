package com.resuelve.preciosve.data.bcv

import com.resuelve.preciosve.domain.bcv.BcvRateSource
import com.resuelve.preciosve.domain.bcv.RateStore
import com.resuelve.preciosve.domain.model.BcvRate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Gestor global de la tasa BCV.
 *
 * - Expone la tasa vigente como [rate] (`StateFlow`) para toda la app.
 * - Prueba las [sources] en orden hasta que una responda con un valor válido.
 * - Si todas fallan conserva la última tasa guardada; si no existe ninguna, [rate] queda en null
 *   y la UI debe mostrar solo precios en Ref.
 * - Solo una actualización se ejecuta a la vez; las llamadas simultáneas esperan y reutilizan el resultado.
 */
class BcvRateManager(
    private val sources: List<BcvRateSource>,
    private val store: RateStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val freshTtlMillis: Long = DEFAULT_FRESH_TTL_MILLIS,
    private val sourceTimeoutMillis: Long = DEFAULT_SOURCE_TIMEOUT_MILLIS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val mutex = Mutex()
    private val _rate = MutableStateFlow<BcvRate?>(null)

    /** Tasa vigente (o la última conocida); null si nunca se obtuvo ninguna. */
    val rate: StateFlow<BcvRate?> = _rate

    /**
     * Carga la tasa persistida (si hace falta) y la actualiza desde red cuando está vencida o
     * [force] es verdadero. Devuelve la mejor tasa disponible tras el intento, o null.
     */
    suspend fun refresh(force: Boolean = false): BcvRate? = mutex.withLock {
        val stored = _rate.value ?: withContext(ioDispatcher) {
            try {
                store.load()
            } catch (e: Exception) {
                null
            }
        }?.also { _rate.value = it }

        val current = _rate.value ?: stored
        if (!force && current != null && current.isFresh(clock(), freshTtlMillis)) {
            return@withLock current
        }

        for (source in sources) {
            val vesPerRef = fetchFrom(source) ?: continue
            val newRate = BcvRate(vesPerRef, clock(), source.name)
            withContext(ioDispatcher) {
                try {
                    store.save(newRate)
                } catch (e: Exception) {
                    // Persistir es un respaldo: un fallo de disco no invalida la tasa en memoria.
                }
            }
            _rate.value = newRate
            return@withLock newRate
        }
        current
    }

    private suspend fun fetchFrom(source: BcvRateSource): Double? = try {
        withTimeout(sourceTimeoutMillis) { source.fetchVesPerRef() }
            .takeIf { it.isFinite() && it > 0.0 }
    } catch (e: TimeoutCancellationException) {
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** La tasa BCV cambia una vez al día: 1 hora es un equilibrio razonable. */
        const val DEFAULT_FRESH_TTL_MILLIS = 60L * 60L * 1_000L
        const val DEFAULT_SOURCE_TIMEOUT_MILLIS = 20_000L
    }
}
