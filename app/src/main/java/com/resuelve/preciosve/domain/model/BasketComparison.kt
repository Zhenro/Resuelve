package com.resuelve.preciosve.domain.model

/**
 * Evaluación comparativa consolidada de toda la cesta de compras
 * en los 3 supermercados caraqueños, incluyendo la Ruta de Súper Ahorro (Canasta Mixta).
 */
data class BasketComparison(
    val totalDistinctProducts: Int,
    val totalUnitsCount: Int,
    val gamaTotalRef: Double,
    val gamaAvailableCount: Int,
    val forumTotalRef: Double,
    val forumAvailableCount: Int,
    val centralTotalRef: Double,
    val centralAvailableCount: Int,
    val winnerStore: Store?,
    val maxSavingsRef: Double,
    val optimalMixedTotalRef: Double = 0.0,
    val optimalMixedSavingsRef: Double = 0.0,
    val purchasedCount: Int = 0,
    val purchasedTotalRef: Double = 0.0,
    val bcvRate: BcvRate?
) {
    val gamaTotalVes: Double
        get() = bcvRate?.vesPerRef?.let { gamaTotalRef * it } ?: 0.0

    val forumTotalVes: Double
        get() = bcvRate?.vesPerRef?.let { forumTotalRef * it } ?: 0.0

    val centralTotalVes: Double
        get() = bcvRate?.vesPerRef?.let { centralTotalRef * it } ?: 0.0

    val maxSavingsVes: Double
        get() = bcvRate?.vesPerRef?.let { maxSavingsRef * it } ?: 0.0

    val optimalMixedTotalVes: Double
        get() = bcvRate?.vesPerRef?.let { optimalMixedTotalRef * it } ?: 0.0

    val optimalMixedSavingsVes: Double
        get() = bcvRate?.vesPerRef?.let { optimalMixedSavingsRef * it } ?: 0.0

    val purchasedTotalVes: Double
        get() = bcvRate?.vesPerRef?.let { purchasedTotalRef * it } ?: 0.0

    val winnerTotalRef: Double
        get() = when (winnerStore) {
            Store.GAMA -> gamaTotalRef
            Store.FORUM -> forumTotalRef
            Store.CENTRAL -> centralTotalRef
            null -> 0.0
        }

    val isEmpty: Boolean
        get() = totalDistinctProducts == 0

    companion object {
        fun empty(): BasketComparison = BasketComparison(
            totalDistinctProducts = 0,
            totalUnitsCount = 0,
            gamaTotalRef = 0.0,
            gamaAvailableCount = 0,
            forumTotalRef = 0.0,
            forumAvailableCount = 0,
            centralTotalRef = 0.0,
            centralAvailableCount = 0,
            winnerStore = null,
            maxSavingsRef = 0.0,
            optimalMixedTotalRef = 0.0,
            optimalMixedSavingsRef = 0.0,
            purchasedCount = 0,
            purchasedTotalRef = 0.0,
            bcvRate = null
        )

        fun compute(items: List<ShoppingItem>, bcvRate: BcvRate?): BasketComparison {
            if (items.isEmpty()) return empty()

            var gamaSum = 0.0
            var gamaCount = 0
            var forumSum = 0.0
            var forumCount = 0
            var centralSum = 0.0
            var centralCount = 0
            var totalUnits = 0
            var optimalMixedSum = 0.0
            var purchasedCount = 0
            var purchasedSum = 0.0

            for (item in items) {
                totalUnits += item.quantity
                item.totalGamaRef?.let {
                    gamaSum += it
                    gamaCount++
                }
                item.totalForumRef?.let {
                    forumSum += it
                    forumCount++
                }
                item.totalCentralRef?.let {
                    centralSum += it
                    centralCount++
                }

                val cheapestItemRef = listOfNotNull(item.totalGamaRef, item.totalForumRef, item.totalCentralRef).minOrNull()
                if (cheapestItemRef != null) {
                    optimalMixedSum += cheapestItemRef
                    if (item.isPurchased) {
                        purchasedCount++
                        purchasedSum += cheapestItemRef
                    }
                }
            }

            // Calculamos la tienda ganadora priorizando disponibilidad completa y luego menor costo
            val candidates = mutableListOf<Triple<Store, Double, Int>>()
            if (gamaCount > 0) candidates.add(Triple(Store.GAMA, gamaSum, gamaCount))
            if (forumCount > 0) candidates.add(Triple(Store.FORUM, forumSum, forumCount))
            if (centralCount > 0) candidates.add(Triple(Store.CENTRAL, centralSum, centralCount))

            val winner = candidates
                .sortedWith(
                    compareByDescending<Triple<Store, Double, Int>> { it.third } // Mayor cantidad de productos disponibles
                        .thenBy { it.second } // Menor precio total
                )
                .firstOrNull()?.first

            // Ahorro calculado frente a los competidores con disponibilidad comparable
            val maxCost = candidates.maxOfOrNull { it.second } ?: 0.0
            val minCost = candidates.minOfOrNull { it.second } ?: 0.0
            val savings = (maxCost - minCost).coerceAtLeast(0.0)

            // Ahorro mixto adicional frente a la mejor tienda individual
            val mixedSavings = (minCost - optimalMixedSum).coerceAtLeast(0.0)

            return BasketComparison(
                totalDistinctProducts = items.size,
                totalUnitsCount = totalUnits,
                gamaTotalRef = gamaSum,
                gamaAvailableCount = gamaCount,
                forumTotalRef = forumSum,
                forumAvailableCount = forumCount,
                centralTotalRef = centralSum,
                centralAvailableCount = centralCount,
                winnerStore = winner,
                maxSavingsRef = savings,
                optimalMixedTotalRef = optimalMixedSum,
                optimalMixedSavingsRef = mixedSavings,
                purchasedCount = purchasedCount,
                purchasedTotalRef = purchasedSum,
                bcvRate = bcvRate
            )
        }
    }
}
