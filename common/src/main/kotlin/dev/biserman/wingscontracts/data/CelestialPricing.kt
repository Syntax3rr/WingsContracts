package dev.biserman.wingscontracts.data

import dev.biserman.wingscontracts.config.ModConfig
import kotlin.math.max

internal data class Shape(
    val baseUnitsDemanded: Int,
    val maxLifetimeUnits: Int,
    val maxLevel: Int,
    val quantityGrowthFactor: Double,
    val cycleDurationMs: Long,
    val expiresIn: Int,
)

internal object CelestialPricing {
    fun premium(shape: Shape): Double {
        val cfg = ModConfig.SERVER

        val isCycled = shape.cycleDurationMs > 0L
        val hasFiniteExpiry = isCycled && shape.expiresIn > 0
        val isUnlimited = shape.maxLifetimeUnits == 0

        val effectiveLifetimeUnits = if (isUnlimited) cfg.celestialUnlimitedLifetimeUnitsRarityCap.get()
        else shape.maxLifetimeUnits

        var p = 1.0
        p *= 1.0 + cfg.celestialCostPerExtraLifetimeUnitFactor.get() * max(0, effectiveLifetimeUnits - 1)
        p *= 1.0 + cfg.celestialCostPerExtraLevelFactor.get() * max(0, shape.maxLevel - 1)
        p *= cycleDurationCostFactor(shape.cycleDurationMs)
        if (hasFiniteExpiry) {
            p *= 1.0 + cfg.celestialCostPerExtraExpiryFactor.get() * shape.expiresIn
        }
        if (isUnlimited) {
            p *= if (isCycled) cfg.celestialUnlimitedLifetimeUnitsMultiplier.get()
            else cfg.celestialUnlimitedNoCycleMultiplier.get()
        }
        if (shape.expiresIn == -1 && isCycled) p *= cfg.celestialNeverExpiresMultiplier.get()

        return p
    }

    fun cycleDurationOptions(): List<Long> =
        ModConfig.SERVER.celestialRandomCycleDurationOptionsMs.get().split(",")
            .mapNotNull { it.trim().toLongOrNull() }

    fun cycleDurationCostFactor(cycleDurationMs: Long): Double {
        val options = cycleDurationOptions()
        val factors = ModConfig.SERVER.celestialCycleDurationCostFactors.get().split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
        if (options.size != factors.size || options.isEmpty()) return 1.0
        val idx = options.indexOf(cycleDurationMs)
        return if (idx >= 0) factors[idx] else 1.0
    }
}
