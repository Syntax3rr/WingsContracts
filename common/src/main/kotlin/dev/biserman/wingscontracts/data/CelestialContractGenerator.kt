package dev.biserman.wingscontracts.data

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.core.CelestialContract
import dev.biserman.wingscontracts.core.Contract.Companion.author
import dev.biserman.wingscontracts.core.Contract.Companion.countPerUnit
import dev.biserman.wingscontracts.core.Contract.Companion.currencyAnchor
import dev.biserman.wingscontracts.core.Contract.Companion.description
import dev.biserman.wingscontracts.core.Contract.Companion.displayItem
import dev.biserman.wingscontracts.core.Contract.Companion.maxLifetimeUnits
import dev.biserman.wingscontracts.core.Contract.Companion.name
import dev.biserman.wingscontracts.core.Contract.Companion.rarity
import dev.biserman.wingscontracts.core.Contract.Companion.startTime
import dev.biserman.wingscontracts.core.Contract.Companion.targetConditions
import dev.biserman.wingscontracts.core.Contract.Companion.targetItems
import dev.biserman.wingscontracts.core.Contract.Companion.type
import dev.biserman.wingscontracts.core.ContractType
import dev.biserman.wingscontracts.core.ServerContract.Companion.baseUnitsDemanded
import dev.biserman.wingscontracts.core.ServerContract.Companion.currentCycleStart
import dev.biserman.wingscontracts.core.ServerContract.Companion.cycleDurationMs
import dev.biserman.wingscontracts.core.ServerContract.Companion.expiresIn
import dev.biserman.wingscontracts.core.ServerContract.Companion.maxLevel
import dev.biserman.wingscontracts.core.ServerContract.Companion.quantityGrowthFactor
import dev.biserman.wingscontracts.core.ServerContract.Companion.reward
import dev.biserman.wingscontracts.data.ContractSavedData.Companion.random
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.Reward
import dev.biserman.wingscontracts.util.DenominationsHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

class CelestialContractGenerator(val data: ContractSavedData) {

    // Resolved every access so `/reload` of `celestialCurrencyAnchor` takes effect immediately.
    val currencyAnchorItem: Item?
        get() {
            val key = ModConfig.SERVER.celestialCurrencyAnchor.get()
            val resolved = ResourceLocation.tryParse(key)?.let { BuiltInRegistries.ITEM[it] }
            return if (resolved == null || resolved == Items.AIR) null else resolved
        }

    private val denominations: Map<Item, Double>?
        get() = currencyAnchorItem?.let { data.currencyHandler.itemToCurrencyMap[it] }

    fun generateContract(): CelestialContract? {
        val definedChance = ModConfig.SERVER.celestialDefinedContractChance.get()
        val defined = ContractDataReloadListener.data.availableCelestialContracts
        if (definedChance > 0.0 && defined.isNotEmpty() && random.nextDouble() < definedChance) {
            val tag = ContractTag(defined.random().tag.copy())
            return CelestialContract.load(tag, data)
        }
        val entry = ContractDataReloadListener.randomCelestialRewardPoolEntry() ?: return null
        return generateContract(entry)
    }

    private fun generateContract(entry: CelestialRewardPoolEntry): CelestialContract? {
        val bias = ModConfig.SERVER.celestialRarityBias.get().coerceIn(-1.0, 1.0)
        val tournamentSize = (1.0 + abs(bias) * MAX_BIAS_TOURNAMENT_BONUS).toInt().coerceAtLeast(1)

        val candidates = (1..tournamentSize).mapNotNull { rollWithRetries(entry) }
        if (candidates.isEmpty()) {
            // Cheapest shape as a last resort so generation always terminates.
            return rollAndPrice(entry, forceCheapShape = true) ?: run {
                WingsContractsMod.LOGGER.warn(
                    "Celestial generator could not satisfy slot-fit even with cheapest shape; entry value=${entry.value}"
                )
                null
            }
        }

        if (candidates.size == 1 || bias == 0.0) return candidates.first()
        return if (bias > 0) candidates.maxBy { it.calculateRarity(data, entry.value) }
        else candidates.minBy { it.calculateRarity(data, entry.value) }
    }

    private fun rollWithRetries(entry: CelestialRewardPoolEntry): CelestialContract? {
        repeat(REROLL_ATTEMPTS) {
            rollAndPrice(entry)?.let { return it }
        }
        return null
    }

    private fun rollAndPrice(entry: CelestialRewardPoolEntry, forceCheapShape: Boolean = false): CelestialContract? {
        val rawShape = if (forceCheapShape) CHEAP_SHAPE else rollShape(entry.shape)

        // Clamp maxLifetimeUnits before the cost calc so an unlimited+expiry roll is priced as the finite cap it will load as.
        val achievable = CelestialContract.maxAchievableLifetimeUnits(
            cycleDurationMs = rawShape.cycleDurationMs,
            expiresIn = rawShape.expiresIn,
            maxLevel = rawShape.maxLevel,
            quantityGrowthFactor = rawShape.quantityGrowthFactor,
            growthFunction = ModConfig.SERVER.celestialContractGrowthFunction.get(),
            baseUnitsDemanded = rawShape.baseUnitsDemanded,
        )
        val shape = rawShape.copy(
            maxLifetimeUnits = CelestialContract.clampMaxLifetimeUnits(rawShape.maxLifetimeUnits, achievable)
        )

        val premium = computePerUnitPremium(shape)

        val rawCost = ceil(
            entry.value * premium * ModConfig.SERVER.celestialBaseCostMultiplier.get() * varyOptional()
        ).toInt().coerceAtLeast(1)

        val demand = pickDemand(rawCost) ?: return null

        val tag = ContractTag(CompoundTag()).apply {
            this.type = ContractType.CELESTIAL
            this.startTime = data.currentCycleStart
            this.currentCycleStart = data.currentCycleStart
            this.cycleDurationMs = shape.cycleDurationMs
            this.countPerUnit = demand.countPerUnit
            this.baseUnitsDemanded = shape.baseUnitsDemanded
            this.expiresIn = shape.expiresIn
            this.maxLifetimeUnits = shape.maxLifetimeUnits
            this.maxLevel = shape.maxLevel
            this.quantityGrowthFactor = shape.quantityGrowthFactor
            this.author = CelestialContract.FALLBACK_AUTHOR
            this.reward = entry.reward
            entry.name?.let { this.name = it }
            entry.description?.let { this.description = it }
            entry.displayItem?.let { this.displayItem = it }

            if (demand.targetItem != null) {
                this.targetItems = listOf(demand.targetItem)
            } else {
                currencyAnchorItem?.let {
                    this.currencyAnchor = BuiltInRegistries.ITEM.getKey(it)?.toString()
                }
            }
        }

        val contract = CelestialContract.load(tag, data)
        contract.save(tag.tag).also { savedTag ->
            savedTag.rarity = contract.calculateRarity(data, entry.value)
        }
        return CelestialContract.load(tag, data)
    }

    private val CHEAP_SHAPE = Shape(
        baseUnitsDemanded = 1,
        maxLifetimeUnits = 1,
        maxLevel = 1,
        quantityGrowthFactor = 1.0,
        cycleDurationMs = 0L,
        expiresIn = -1,
    )

    private fun rollShape(constraints: ShapeConstraints?): Shape {
        val cfg = ModConfig.SERVER

        val baseUnitsLo = constraints?.baseUnitsDemandedMin ?: cfg.celestialRandomBaseUnitsDemandedMin.get()
        val baseUnitsHi = constraints?.baseUnitsDemandedMax ?: cfg.celestialRandomBaseUnitsDemandedMax.get()
        val baseUnitsDemanded = randomIntInclusive(baseUnitsLo, baseUnitsHi).coerceAtLeast(1)

        val unlimitedChance =
            constraints?.unlimitedLifetimeUnitsChance ?: cfg.celestialRandomUnlimitedLifetimeUnitsChance.get()
        val maxLifetimeUnits = if (random.nextDouble() < unlimitedChance) {
            0
        } else {
            val lo = constraints?.maxLifetimeUnitsMin ?: cfg.celestialRandomMaxLifetimeUnitsMin.get()
            val hi = constraints?.maxLifetimeUnitsMax ?: cfg.celestialRandomMaxLifetimeUnitsMax.get()
            randomIntInclusive(lo, hi)
        }

        val levelLo = constraints?.maxLevelMin ?: cfg.celestialRandomMaxLevelMin.get()
        val levelHi = constraints?.maxLevelMax ?: cfg.celestialRandomMaxLevelMax.get()
        val maxLevel = randomIntInclusive(levelLo, levelHi)
        val growthFactor = when {
            constraints?.quantityGrowthFactor != null -> constraints.quantityGrowthFactor
            maxLevel <= 1 -> 1.0
            else -> {
                val gLo = constraints?.quantityGrowthFactorMin ?: cfg.celestialRandomQuantityGrowthFactorMin.get()
                val gHi = constraints?.quantityGrowthFactorMax ?: cfg.celestialRandomQuantityGrowthFactorMax.get()
                gLo + random.nextDouble() * (gHi - gLo)
            }
        }

        val cycleDurationMs = pickCycleDuration(constraints, maxLifetimeUnits)

        val expiresIn = if (cycleDurationMs > 0L) {
            val neverChance = constraints?.neverExpiresChance ?: cfg.celestialRandomNeverExpiresChance.get()
            if (random.nextDouble() < neverChance) -1
            else {
                val lo = constraints?.expiresInMin ?: cfg.celestialRandomExpiresInMin.get()
                val hi = constraints?.expiresInMax ?: cfg.celestialRandomExpiresInMax.get()
                randomIntInclusive(lo, hi)
            }
        } else -1

        return Shape(baseUnitsDemanded, maxLifetimeUnits, maxLevel, growthFactor, cycleDurationMs, expiresIn)
    }

    private fun pickCycleDuration(constraints: ShapeConstraints?, maxLifetimeUnits: Int): Long {
        val (options, rawWeights) = if (
            constraints?.cycleDurationOptionsMs != null && constraints.cycleDurationWeights != null
            && constraints.cycleDurationOptionsMs.size == constraints.cycleDurationWeights.size
            && constraints.cycleDurationOptionsMs.isNotEmpty()
        ) {
            constraints.cycleDurationOptionsMs to constraints.cycleDurationWeights
        } else {
            val cfgOptions = ModConfig.SERVER.celestialRandomCycleDurationOptionsMs.get().split(",")
                .mapNotNull { it.trim().toLongOrNull() }
            val cfgWeights = ModConfig.SERVER.celestialRandomCycleDurationWeights.get().split(",")
                .mapNotNull { it.trim().toIntOrNull() }
            if (cfgOptions.isEmpty() || cfgOptions.size != cfgWeights.size) {
                return 0L  // misconfigured, no cycle
            }
            cfgOptions to cfgWeights
        }

        // Suppress non-zero cycle weights when maxLifetimeUnits is finite (>0). Unlimited shapes are unaffected.
        val suppression = ModConfig.SERVER.celestialFiniteLifetimeUnitsCycleSuppressionFactor.get()
        val weights = if (maxLifetimeUnits > 0 && suppression < 1.0) {
            rawWeights.zip(options).map { (w, opt) ->
                if (opt == 0L) w
                else if (suppression == 0.0) 0
                // Round up so a low-but-nonzero suppression doesn't silently zero out small weights.
                else max(1, ceil(w * suppression).toInt())
            }
        } else rawWeights

        val totalWeight = weights.sum()
        if (totalWeight <= 0) return 0L
        var pick = random.nextInt(totalWeight)
        for (i in options.indices) {
            pick -= weights[i]
            if (pick < 0) return options[i]
        }
        return options.last()
    }

    private fun computePerUnitPremium(shape: Shape): Double = CelestialPricing.premium(shape)

    private fun varyOptional(): Double {
        val variance = ModConfig.SERVER.celestialVariance.get()
        if (variance <= 0.0) return 1.0
        val r = random.nextDouble()
        return 1.0 + (r * 2.0 - 1.0) * variance
    }

    // null targetItem means "use currency anchor", non-null means "use this item as the target".
    private data class DemandSpec(
        val targetItem: Item?,
        val countPerUnit: Int,
    )

    private fun pickDemand(rawCost: Int): DemandSpec? {
        val swapRate = ModConfig.SERVER.celestialReplaceCurrencyTargetRate.get()
        if (random.nextDouble() < swapRate) {
            trySwapTarget(rawCost)?.let { return it }
        }

        if (currencyAnchorItem == null) return null
        val countPerUnit = applySlotFit(rawCost) ?: return null
        return DemandSpec(targetItem = null, countPerUnit = countPerUnit)
    }

    private fun trySwapTarget(rawCost: Int): DemandSpec? {
        val factor = ModConfig.SERVER.celestialReplaceCurrencyTargetFactor.get()
        val defaultRewardMultiplier = ModConfig.SERVER.defaultRewardMultiplier.get()
        val portalSlots = ModConfig.SERVER.contractPortalInputSlots.get()
        val anchor = currencyAnchorItem

        repeat(SWAP_ATTEMPTS) {
            val otherTag = ContractDataReloadListener.randomTag()
            val otherTargets = otherTag.targetItems ?: return@repeat
            if (otherTargets.size != 1) return@repeat
            if ((otherTag.targetConditions?.size ?: 0) != 0) return@repeat

            val otherItem = otherTargets[0]
            if (otherItem == Items.AIR) return@repeat
            if (otherItem == anchor) return@repeat
            // The swapped-in target must not itself be a denominated currency.
            if (data.currencyHandler.isCurrency(otherItem.defaultInstance)) return@repeat

            val otherKey = BuiltInRegistries.ITEM.getKey(otherItem)?.toString() ?: return@repeat
            if (ContractDataReloadListener.data.rewardBlocklist.contains(otherKey)) return@repeat

            val otherReward = otherTag.reward ?: return@repeat
            if (otherReward !is Reward.Random) return@repeat

            val otherCountPerUnit = (otherTag.countPerUnit ?: 16).toDouble()
            val otherRewardValue = otherReward.value * defaultRewardMultiplier
            if (otherRewardValue <= 0.0) return@repeat

            val newCount = ceil(rawCost * otherCountPerUnit * factor / otherRewardValue).toInt()
            if (newCount <= 0) return@repeat
            if (newCount > portalSlots * otherItem.defaultMaxStackSize) return@repeat

            return DemandSpec(targetItem = otherItem, countPerUnit = newCount)
        }
        return null
    }

    private fun applySlotFit(rawCost: Int): Int? {
        val portalSlots = ModConfig.SERVER.contractPortalInputSlots.get()
        val denoms = denominations
        if (denoms == null || denoms.isEmpty()) {
            val anchor = currencyAnchorItem ?: return rawCost
            val capacity = portalSlots * anchor.defaultMaxStackSize
            return if (rawCost <= capacity) rawCost else null
        }

        if (slotsRequired(rawCost, denoms) <= portalSlots) return rawCost

        val (dMaxItem, dMaxValue) = denoms.maxByOrNull { it.value } ?: return null
        if (dMaxValue <= 0.0) return null
        val rounded = ceil(rawCost / dMaxValue).toInt() * dMaxValue.toInt()
        if (rounded < rawCost) return null  // sanity
        if (slotsRequired(rounded, denoms) <= portalSlots) return rounded

        val absoluteCeiling = portalSlots.toDouble() * dMaxItem.defaultMaxStackSize * dMaxValue
        if (rounded > absoluteCeiling) return null
        return rounded  // technically fits but no further reduction available
    }

    private fun slotsRequired(cost: Int, denoms: Map<Item, Double>): Int {
        if (cost <= 0) return 0
        var total = 0
        val parts = DenominationsHelper.denominate(cost.toDouble(), denoms)
        parts.forEach { pair ->
            val item: Item = pair.first
            val count: Int = pair.second
            total += ceil(count.toDouble() / item.defaultMaxStackSize).toInt()
        }
        return total
    }

    companion object {
        private const val REROLL_ATTEMPTS = 5
        private const val SWAP_ATTEMPTS = 5
        private const val MAX_BIAS_TOURNAMENT_BONUS = 5
    }
}
