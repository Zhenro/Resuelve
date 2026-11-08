package com.resuelve.preciosve.domain.model

/**
 * Conjunto de productos equivalentes de distintas tiendas, ordenado del más barato
 * al más caro por [ProductItem.totalPriceRef].
 *
 * Contrato: [items] no está vacío y ya viene ordenado. Use [create] para ordenar.
 */
data class ComparisonGroup(
    val key: String,
    val displayName: String,
    val items: List<ProductItem>
) {
    init {
        require(key.isNotBlank()) { "La clave del grupo no puede estar vacía" }
        require(items.isNotEmpty()) { "Un grupo de comparación necesita al menos un producto" }
        for (i in 1 until items.size) {
            require(items[i - 1].totalPriceRef <= items[i].totalPriceRef) {
                "Los productos del grupo '$key' deben estar ordenados por totalPriceRef"
            }
        }
    }

    /** Producto más barato del grupo. */
    val cheapest: ProductItem
        get() = items.first()

    /** Producto más caro del grupo. */
    val mostExpensive: ProductItem
        get() = items.last()

    /** Ahorro máximo en Ref. al elegir el más barato frente al más caro. */
    val savingsRef: Double
        get() = mostExpensive.totalPriceRef - cheapest.totalPriceRef

    /** Devuelve el producto más barato de [store] dentro del grupo, o null si no está. */
    fun cheapestOf(store: Store): ProductItem? = items.firstOrNull { it.store == store }

    /** Copia con todos los precios en Bs. recalculados (el orden por Ref. no cambia). */
    fun withBcvRate(rate: BcvRate?): ComparisonGroup =
        copy(items = items.map { it.withBcvRate(rate) })

    companion object {

        /**
         * Crea un grupo ordenando [items] por precio total en Ref.
         * Si un supermercado tiene múltiples entradas para el mismo producto,
         * se conserva la más económica para garantizar una comparación limpia y sin redundancia.
         */
        fun create(key: String, displayName: String, items: List<ProductItem>): ComparisonGroup {
            val deduplicatedByStore = items
                .groupBy { it.store }
                .mapValues { (_, storeItems) -> storeItems.minByOrNull { it.totalPriceRef }!! }
                .values
                .sortedWith(ProductItem.PRICE_ORDER)

            return ComparisonGroup(
                key = key,
                displayName = displayName,
                items = deduplicatedByStore
            )
        }
    }
}
