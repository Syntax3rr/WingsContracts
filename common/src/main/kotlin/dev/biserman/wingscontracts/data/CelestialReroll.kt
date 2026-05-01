package dev.biserman.wingscontracts.data

import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.core.Contract.Companion.countPerUnit
import dev.biserman.wingscontracts.core.Contract.Companion.maxLifetimeUnits
import dev.biserman.wingscontracts.core.Contract.Companion.name
import dev.biserman.wingscontracts.core.Contract.Companion.rarity
import dev.biserman.wingscontracts.core.Contract.Companion.relaxCount
import dev.biserman.wingscontracts.core.Contract.Companion.rerollCount
import dev.biserman.wingscontracts.core.Contract.Companion.type
import dev.biserman.wingscontracts.core.Contract.Companion.unitsFulfilledEver
import dev.biserman.wingscontracts.core.ContractType
import dev.biserman.wingscontracts.core.ServerContract.Companion.baseUnitsDemanded
import dev.biserman.wingscontracts.core.ServerContract.Companion.cycleDurationMs
import dev.biserman.wingscontracts.core.ServerContract.Companion.expiresIn
import dev.biserman.wingscontracts.core.ServerContract.Companion.maxLevel
import dev.biserman.wingscontracts.core.ServerContract.Companion.quantityGrowthFactor
import dev.biserman.wingscontracts.core.CelestialContract
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import kotlin.math.ceil
import kotlin.math.max

enum class RerollDirection { RELAX, RESTRICT }

enum class RerollStat { LIFETIME, EXPIRY, CYCLE, LEVEL }

sealed class RerollResult {
    data class Success(val stat: RerollStat, val rerollCount: Int) : RerollResult()
    data object NoApplicableStat : RerollResult()
    data object AlreadyUsed : RerollResult()
    data object MaxRerollsReached : RerollResult()
    data object NotACelestialContract : RerollResult()
}

fun isRerollableCelestialTag(tag: ContractTag): Boolean =
    tag.type == ContractType.CELESTIAL
        && (tag.unitsFulfilledEver ?: 0L) <= 0L
        && canRerollMore(tag)

fun isRerollableCelestialStack(stack: ItemStack): Boolean {
    val tag = ContractTagHelper.getContractTag(stack) ?: return false
    return isRerollableCelestialTag(tag)
}

fun canRerollMore(tag: ContractTag): Boolean {
    val cap = rerollCap(tag)
    if (cap < 0) return true
    return (tag.rerollCount ?: 0) < cap
}

private fun rerollCap(tag: ContractTag): Int {
    val constraints = tag.name?.let { ContractDataReloadListener.getCelestialRewardPoolEntryByName(it)?.shape }
    return constraints?.maxRerolls ?: ModConfig.SERVER.celestialDefaultMaxRerolls.get()
}

internal fun randomIntInclusive(lo: Int, hi: Int): Int {
    if (hi <= lo) return lo
    return lo + ContractSavedData.random.nextInt(hi - lo + 1)
}

object CelestialReroll {
    private const val STAT_PICK_ATTEMPTS = 8

    fun applyReroll(stack: ItemStack, direction: RerollDirection, data: ContractSavedData): RerollResult {
        val live = ContractTagHelper.getContractTag(stack) ?: return RerollResult.NotACelestialContract
        if (live.type != ContractType.CELESTIAL) return RerollResult.NotACelestialContract
        if ((live.unitsFulfilledEver ?: 0L) > 0L) return RerollResult.AlreadyUsed
        if (!canRerollMore(live)) return RerollResult.MaxRerollsReached
        // Defensive copy: setContractTag wraps `tag.tag` in a fresh CustomData, but ItemStack.set
        // short-circuits when the new component equals the old. Mutating the live tag in-place would
        // make the new CustomData compare equal to the stale one, and the swap would silently no-op.
        val tag = ContractTag(live.tag.copy())

        val oldShape = readShape(tag)
        val constraints = tag.name?.let { ContractDataReloadListener.getCelestialRewardPoolEntryByName(it)?.shape }
        val weights = parseWeights(
            if (direction == RerollDirection.RELAX) ModConfig.SERVER.celestialRerollLightningStatWeights.get()
            else ModConfig.SERVER.celestialRerollFireStatWeights.get()
        )

        val (newShape, mutatedStat) = pickAndMutate(oldShape, direction, weights, constraints)
            ?: return RerollResult.NoApplicableStat

        writeShape(tag, newShape)
        val oldRerollCount = tag.rerollCount ?: 0
        val newRerollCount = oldRerollCount + 1
        val oldRelaxCount = tag.relaxCount ?: 0
        // Only RELAX bumps the price markup so a RESTRICT step never costs more than the shape it
        // moved away from. Both directions still count toward the rerollCount cap.
        val newRelaxCount = if (direction == RerollDirection.RELAX) oldRelaxCount + 1 else oldRelaxCount

        val newCount = recomputeCountPerUnit(
            currentCountPerUnit = tag.countPerUnit ?: 1,
            oldShape = oldShape,
            newShape = newShape,
            oldRelaxCount = oldRelaxCount,
            newRelaxCount = newRelaxCount,
        )
        tag.countPerUnit = newCount
        tag.rerollCount = newRerollCount
        tag.relaxCount = newRelaxCount

        val newRarity = CelestialContract.rarityFromCost(newCount, newShape.maxLifetimeUnits, data)
        tag.rarity = newRarity
        val contract = CelestialContract.load(tag, data)

        ContractTagHelper.setContractTag(stack, tag)
        // Mirror Contract.createItem: vanilla DataComponents.RARITY drives the name colour in tooltips.
        stack.set(DataComponents.RARITY, Rarity.BY_ID.apply(newRarity))
        // LoadedContracts is UUID-keyed; without this update tooltips return the pre-reroll Contract
        // until the cache gets cleared on world reload.
        LoadedContracts.update(contract)
        return RerollResult.Success(mutatedStat, newRerollCount)
    }

    private fun pickAndMutate(
        old: Shape,
        dir: RerollDirection,
        weights: List<Int>,
        constraints: ShapeConstraints?,
    ): Pair<Shape, RerollStat>? {
        val random = ContractSavedData.random
        val remaining = weights.toMutableList()
        repeat(STAT_PICK_ATTEMPTS) {
            val total = remaining.sum()
            if (total <= 0) return null
            val idx = pickWeightedIndex(remaining, random.nextInt(total))
            val stat = RerollStat.entries[idx]
            mutateStat(old, stat, dir, constraints)?.let { return it to stat }
            remaining[idx] = 0
        }
        return null
    }

    private fun pickWeightedIndex(weights: List<Int>, roll: Int): Int {
        var remaining = roll
        for (i in weights.indices) {
            remaining -= weights[i]
            if (remaining < 0) return i
        }
        return weights.lastIndex
    }

    private fun mutateStat(
        shape: Shape, stat: RerollStat, dir: RerollDirection, c: ShapeConstraints?
    ): Shape? = when (stat) {
        RerollStat.LIFETIME -> rerollLifetime(shape, dir, c)
        RerollStat.EXPIRY -> rerollExpiry(shape, dir, c)
        RerollStat.CYCLE -> rerollCycle(shape, dir, c)
        RerollStat.LEVEL -> rerollLevel(shape, dir, c)
    }

    private fun rerollLifetime(shape: Shape, dir: RerollDirection, c: ShapeConstraints?): Shape? {
        val cfg = ModConfig.SERVER
        val lo = c?.maxLifetimeUnitsMin ?: cfg.celestialRandomMaxLifetimeUnitsMin.get()
        val hi = c?.maxLifetimeUnitsMax ?: cfg.celestialRandomMaxLifetimeUnitsMax.get()
        // Treat an entry that disables unlimited (chance == 0) as forbidding the unlimited extreme.
        val unlimitedAllowed = (c?.unlimitedLifetimeUnitsChance ?: 1.0) > 0.0
        val current = shape.maxLifetimeUnits

        return when (dir) {
            RerollDirection.RELAX -> when {
                current == 0 -> null
                current >= hi -> if (unlimitedAllowed) shape.copy(maxLifetimeUnits = 0) else null
                else -> shape.copy(maxLifetimeUnits = randomIntInclusive(current + 1, hi))
            }
            RerollDirection.RESTRICT -> when {
                current == 0 -> shape.copy(maxLifetimeUnits = randomIntInclusive(lo, hi))
                current <= lo -> null
                else -> shape.copy(maxLifetimeUnits = randomIntInclusive(lo, current - 1))
            }
        }
    }

    private fun rerollExpiry(shape: Shape, dir: RerollDirection, c: ShapeConstraints?): Shape? {
        if (shape.cycleDurationMs <= 0L) return null
        val cfg = ModConfig.SERVER
        val lo = max(1, c?.expiresInMin ?: cfg.celestialRandomExpiresInMin.get())
        val hi = c?.expiresInMax ?: cfg.celestialRandomExpiresInMax.get()
        val neverAllowed = (c?.neverExpiresChance ?: 1.0) > 0.0
        val current = shape.expiresIn

        return when (dir) {
            RerollDirection.RELAX -> when {
                current < 0 -> null
                current >= hi -> if (neverAllowed) shape.copy(expiresIn = -1) else null
                else -> shape.copy(expiresIn = randomIntInclusive(current + 1, hi))
            }
            RerollDirection.RESTRICT -> when {
                current < 0 -> shape.copy(expiresIn = randomIntInclusive(lo, hi))
                current <= lo -> null
                else -> shape.copy(expiresIn = randomIntInclusive(lo, current - 1))
            }
        }
    }

    private fun rerollCycle(shape: Shape, dir: RerollDirection, c: ShapeConstraints?): Shape? {
        val options = c?.cycleDurationOptionsMs ?: CelestialPricing.cycleDurationOptions()
        if (options.isEmpty()) return null
        val current = shape.cycleDurationMs
        val currentFactor = CelestialPricing.cycleDurationCostFactor(current)
        // Cost factor (not raw ms) defines "more relaxed": higher factor = stronger contract = relaxed.
        // If all configured factors collapse to the same value the candidate list is empty and the
        // caller drops cycle from the weight pool, falling through to a different stat.
        val candidates = when (dir) {
            RerollDirection.RELAX -> options.filter { CelestialPricing.cycleDurationCostFactor(it) > currentFactor }
            RerollDirection.RESTRICT -> options.filter { CelestialPricing.cycleDurationCostFactor(it) < currentFactor }
        }
        if (candidates.isEmpty()) return null
        val pickedCycle = candidates[ContractSavedData.random.nextInt(candidates.size)]
        // Switching to no-cycle drops expiresIn; switching from no-cycle reuses the prior value or defaults to never.
        val newExpiry = when {
            pickedCycle == 0L -> -1
            current == 0L -> shape.expiresIn.takeIf { it != 0 } ?: -1
            else -> shape.expiresIn
        }
        return shape.copy(cycleDurationMs = pickedCycle, expiresIn = newExpiry)
    }

    private fun rerollLevel(shape: Shape, dir: RerollDirection, c: ShapeConstraints?): Shape? {
        val cfg = ModConfig.SERVER
        val lo = c?.maxLevelMin ?: cfg.celestialRandomMaxLevelMin.get()
        val hi = c?.maxLevelMax ?: cfg.celestialRandomMaxLevelMax.get()
        val current = shape.maxLevel

        val newLevel = when (dir) {
            RerollDirection.RELAX -> if (current >= hi) return null else randomIntInclusive(current + 1, hi)
            RerollDirection.RESTRICT -> if (current <= lo) return null else randomIntInclusive(lo, current - 1)
        }
        return shape.copy(maxLevel = newLevel, quantityGrowthFactor = pickGrowthFactor(newLevel, shape, c))
    }

    // Mirrors CelestialContractGenerator.rollShape's growth-factor logic so the field stays
    // consistent with the new level: 1.0 at level <= 1, otherwise rolled within constraints.
    private fun pickGrowthFactor(maxLevel: Int, shape: Shape, c: ShapeConstraints?): Double {
        val cfg = ModConfig.SERVER
        return when {
            c?.quantityGrowthFactor != null -> c.quantityGrowthFactor
            maxLevel <= 1 -> 1.0
            // Preserve the existing factor when staying in multi-level territory so a level reroll
            // doesn't churn the demand curve unexpectedly.
            shape.maxLevel > 1 -> shape.quantityGrowthFactor
            else -> {
                val gLo = c?.quantityGrowthFactorMin ?: cfg.celestialRandomQuantityGrowthFactorMin.get()
                val gHi = c?.quantityGrowthFactorMax ?: cfg.celestialRandomQuantityGrowthFactorMax.get()
                gLo + ContractSavedData.random.nextDouble() * (gHi - gLo)
            }
        }
    }

    private fun recomputeCountPerUnit(
        currentCountPerUnit: Int,
        oldShape: Shape,
        newShape: Shape,
        oldRelaxCount: Int,
        newRelaxCount: Int,
    ): Int {
        val penalty = ModConfig.SERVER.celestialRerollCostPenaltyFactor.get()
        val oldMarkup = 1.0 + oldRelaxCount * penalty
        val newMarkup = 1.0 + newRelaxCount * penalty
        val oldPremium = CelestialPricing.premium(oldShape)
        val newPremium = CelestialPricing.premium(newShape)
        if (oldPremium <= 0.0) return currentCountPerUnit
        val unmarkedOld = currentCountPerUnit / oldMarkup
        val unmarkedNew = unmarkedOld * (newPremium / oldPremium)
        return ceil(unmarkedNew * newMarkup).toInt().coerceAtLeast(1)
    }

    private fun readShape(tag: ContractTag): Shape = Shape(
        baseUnitsDemanded = tag.baseUnitsDemanded ?: 1,
        maxLifetimeUnits = tag.maxLifetimeUnits ?: CelestialContract.FALLBACK_MAX_LIFETIME_UNITS,
        maxLevel = tag.maxLevel ?: CelestialContract.FALLBACK_MAX_LEVEL,
        quantityGrowthFactor = tag.quantityGrowthFactor ?: CelestialContract.FALLBACK_QUANTITY_GROWTH_FACTOR,
        cycleDurationMs = tag.cycleDurationMs ?: CelestialContract.FALLBACK_CYCLE_DURATION_MS,
        expiresIn = tag.expiresIn ?: CelestialContract.FALLBACK_EXPIRES_IN,
    )

    private fun writeShape(tag: ContractTag, shape: Shape) {
        tag.maxLifetimeUnits = shape.maxLifetimeUnits
        tag.maxLevel = shape.maxLevel
        tag.quantityGrowthFactor = shape.quantityGrowthFactor
        tag.cycleDurationMs = shape.cycleDurationMs
        tag.expiresIn = shape.expiresIn
        tag.baseUnitsDemanded = shape.baseUnitsDemanded
    }

    private fun parseWeights(raw: String): List<Int> {
        val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        // Pad / truncate to match the number of stats; fall back to uniform on garbage input.
        val padded = parsed + List((RerollStat.entries.size - parsed.size).coerceAtLeast(0)) { 1 }
        return padded.take(RerollStat.entries.size).map { it.coerceAtLeast(0) }
    }
}
