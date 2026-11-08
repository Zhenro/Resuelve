package com.resuelve.preciosve.domain.model

/**
 * Tasa oficial BCV: cuántos bolívares equivalen a 1 unidad de referencia (Ref.).
 *
 * @property vesPerRef Bolívares por 1 Ref. Siempre finita y mayor que cero.
 * @property fetchedAtMillis Instante (epoch ms) en que se obtuvo la tasa.
 * @property source Nombre de la fuente que la entregó (para depuración y UI).
 */
data class BcvRate(
    val vesPerRef: Double,
    val fetchedAtMillis: Long,
    val source: String
) {
    init {
        require(vesPerRef.isFinite() && vesPerRef > 0.0) {
            "La tasa BCV debe ser un número finito mayor que cero: $vesPerRef"
        }
    }

    /**
     * Indica si la tasa sigue considerándose vigente.
     * Si el reloj del dispositivo retrocedió (edad negativa) se considera vencida.
     */
    fun isFresh(nowMillis: Long, ttlMillis: Long): Boolean {
        val age = nowMillis - fetchedAtMillis
        return age in 0..ttlMillis
    }
}
