package com.resuelve.preciosve.domain.bcv

/**
 * Fuente de la tasa BCV (bolívares por 1 Ref.). Puede ser el banner de Gama,
 * una API pública, etc. El gestor las prueba en orden hasta que una responda.
 */
interface BcvRateSource {

    /** Nombre legible de la fuente. */
    val name: String

    /** Obtiene la tasa en bolívares por Ref.; lanza excepción si no puede hacerlo. */
    suspend fun fetchVesPerRef(): Double
}
