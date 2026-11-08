package com.resuelve.preciosve.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Producto normalizado, común a las tres cadenas.
 *
 * @property id Identificador único y estable (prefijado por tienda) usado como clave de DiffUtil.
 * @property store Cadena de origen.
 * @property name Nombre limpio con peso/presentación normalizados.
 * @property basePriceRef Precio sin IVA en divisas (Ref.).
 * @property totalPriceRef Precio final con IVA incluido en Ref.: campo clave de ordenamiento.
 * @property totalPriceVes Precio en bolívares = [totalPriceRef] × tasa BCV; 0.0 si aún no hay tasa.
 * @property imageUrl URL de la imagen (vacía si no existe).
 * @property productUrl URL pública del producto (vacía si no existe).
 */
data class ProductItem(
    val id: String,
    val store: Store,
    val name: String,
    val basePriceRef: Double,
    val totalPriceRef: Double,
    val totalPriceVes: Double,
    val imageUrl: String,
    val productUrl: String
) {

    /** Verdadero si el precio es utilizable para comparar (finito y positivo). */
    val isComparable: Boolean
        get() = totalPriceRef.isFinite() && totalPriceRef > 0.0

    /** Verdadero si [totalPriceVes] contiene un valor calculado con una tasa real. */
    val hasVes: Boolean
        get() = totalPriceVes > 0.0

    /** Devuelve una copia con [totalPriceVes] recalculado; sin tasa queda en 0.0. */
    fun withBcvRate(rate: BcvRate?): ProductItem =
        copy(totalPriceVes = calculateVes(totalPriceRef, rate?.vesPerRef))

    companion object {

        /** Orden de comparación: más barato primero, luego tienda y nombre (determinista). */
        val PRICE_ORDER: Comparator<ProductItem> =
            compareBy<ProductItem>({ it.totalPriceRef }, { it.store.ordinal }, { it.name })

        /**
         * Convierte Ref. a Bs. redondeando a 2 decimales (HALF_UP) sobre decimales exactos,
         * evitando errores de punto flotante. Devuelve 0.0 si la tasa falta o es inválida.
         */
        fun calculateVes(totalPriceRef: Double, vesPerRef: Double?): Double {
            if (vesPerRef == null || !vesPerRef.isFinite() || vesPerRef <= 0.0) return 0.0
            if (!totalPriceRef.isFinite() || totalPriceRef < 0.0) return 0.0
            return BigDecimal.valueOf(totalPriceRef)
                .multiply(BigDecimal.valueOf(vesPerRef))
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
        }
    }
}
