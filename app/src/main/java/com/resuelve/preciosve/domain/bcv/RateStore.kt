package com.resuelve.preciosve.domain.bcv

import com.resuelve.preciosve.domain.model.BcvRate

/** Almacén persistente de la última tasa BCV conocida (respaldo sin conexión). */
interface RateStore {

    /** Devuelve la última tasa guardada o null si no existe o está corrupta. */
    fun load(): BcvRate?

    /** Guarda [rate] como la última tasa conocida. */
    fun save(rate: BcvRate)
}
