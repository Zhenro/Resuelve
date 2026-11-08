package com.resuelve.preciosve.data.bcv

import android.content.Context
import android.content.SharedPreferences
import com.resuelve.preciosve.domain.bcv.RateStore
import com.resuelve.preciosve.domain.model.BcvRate

/**
 * Persiste la última tasa BCV en SharedPreferences. El `Double` se guarda como bits de `Long`
 * para no perder precisión.
 */
class SharedPreferencesRateStore(private val prefs: SharedPreferences) : RateStore {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override fun load(): BcvRate? {
        if (!prefs.contains(KEY_RATE_BITS)) return null
        return try {
            val vesPerRef = Double.fromBits(prefs.getLong(KEY_RATE_BITS, 0L))
            val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
            val source = prefs.getString(KEY_SOURCE, null) ?: UNKNOWN_SOURCE
            BcvRate(vesPerRef, fetchedAt, source)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: ClassCastException) {
            null
        }
    }

    override fun save(rate: BcvRate) {
        prefs.edit()
            .putLong(KEY_RATE_BITS, rate.vesPerRef.toRawBits())
            .putLong(KEY_FETCHED_AT, rate.fetchedAtMillis)
            .putString(KEY_SOURCE, rate.source)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "bcv_rate_prefs"
        const val KEY_RATE_BITS = "rate_bits"
        const val KEY_FETCHED_AT = "fetched_at"
        const val KEY_SOURCE = "source"
        const val UNKNOWN_SOURCE = "desconocida"
    }
}
