package dev.biserman.wingscontracts.core

import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.config.DecayFunctionOptions
import dev.biserman.wingscontracts.config.GrowthFunctionOptions
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ContractTagHelper.boolean
import dev.biserman.wingscontracts.nbt.ContractTagHelper.double
import dev.biserman.wingscontracts.nbt.ContractTagHelper.enum
import dev.biserman.wingscontracts.nbt.ContractTagHelper.int
import dev.biserman.wingscontracts.nbt.ContractTagHelper.long
import dev.biserman.wingscontracts.nbt.ContractTagHelper.reward
import dev.biserman.wingscontracts.nbt.ItemCondition
import dev.biserman.wingscontracts.util.DenominationsHelper
import net.minecraft.ChatFormatting
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

@Suppress("MemberVisibilityCanBePrivate")
abstract class ServerContract(
    // Identity & targeting
    id: UUID,
    targetItems: List<Item>,
    targetTags: List<TagKey<Item>>,
    targetBlockTags: List<TagKey<Block>>,
    targetConditions: List<ItemCondition>,

    // Cycle timing
    startTime: Long,
    var currentCycleStart: Long,
    val cycleDurationMs: Long,

    // Demand & fulfillment
    countPerUnit: Int,
    val baseUnitsDemanded: Int,
    var unitsFulfilled: Int,
    unitsFulfilledEver: Long,
    var expiresIn: Int,

    // Display metadata
    author: String,
    name: String?,
    description: String?,
    shortTargetList: String?,
    displayItem: ItemStack?,
    rarity: Int?,

    val reward: ContractReward,

    // Leveling
    var level: Int,
    val quantityGrowthFactor: Double,
    val maxLevel: Int,

    // Decay
    val decayEnabled: Boolean,
    val decayCyclesPerEvent: Int,
    val decayLevelsPerEvent: Int,
    val decayPercentPerEvent: Double,
    val decayMinLevel: Int,
    var decayProgress: Int,
    val decayFunctionOverride: DecayFunctionOptions?,

    // State
    isActive: Boolean,
    maxFulfilments: Int,
    var isInitialized: Boolean,

    currencyAnchor: Item? = null,
) : Contract(
    id,
    targetItems,
    targetTags,
    targetBlockTags,
    targetConditions,
    startTime,
    countPerUnit,
    unitsFulfilledEver,
    author,
    name,
    description,
    shortTargetList,
    displayItem,
    rarity,
    isActive,
    maxFulfilments,
    currencyAnchor,
) {
    abstract val growthFunction: GrowthFunctionOptions
    abstract val defaultDecayFunction: DecayFunctionOptions
    val decayFunction: DecayFunctionOptions get() = decayFunctionOverride ?: defaultDecayFunction

    val hasEffectiveDecay: Boolean
        get() = when (decayFunction) {
            DecayFunctionOptions.FIXED -> decayLevelsPerEvent > 0
            DecayFunctionOptions.PERCENTAGE -> decayPercentPerEvent > 0.0
        }

    abstract fun calculateRarity(data: ContractSavedData, rewardUnitValue: Double): Int

    override val rewardPerUnit get() = reward.rewardPerUnit
    override val isComplete get() = unitsFulfilled >= unitsDemanded

    /** True when `maxFulfilments` will complete before we hit `unitsDemanded`. */
    val willCapBeforeLevelUp: Boolean
        get() = maxFulfilments in 1..unitsDemanded

    val unitsDemanded: Int get() = unitsDemandedAtLevel(level)

    open fun unitsDemandedAtLevel(level: Int): Int {
        if (countPerUnit == 0) {
            return 0
        }

        return when (val fn = growthFunction) {
            GrowthFunctionOptions.LINEAR -> {
                val growth = (baseUnitsDemanded * (level - 1) * (quantityGrowthFactor - 1)).toInt()
                baseUnitsDemanded + growth
            }

            GrowthFunctionOptions.EXPONENTIAL -> (baseUnitsDemanded * quantityGrowthFactor.pow(level - 1)).toInt()
            else -> throw Error("Unrecognized contract growth function: $fn")
        }
    }

    val cyclesPassed get() = ((System.currentTimeMillis() - currentCycleStart) / cycleDurationMs).toInt()
    val newCycleStart get() = currentCycleStart + cycleDurationMs * cyclesPassed

    val maxUnitsDemanded: Int
        get() {
            if (maxLevel <= 0) {
                val compare = unitsDemandedAtLevel(1).compareTo(unitsDemandedAtLevel(2))
                return when {
                    compare < 0 -> Int.MAX_VALUE
                    compare == 0 -> unitsDemandedAtLevel(1)
                    else -> 1
                }
            }
            return max(unitsDemandedAtLevel(1), unitsDemandedAtLevel(maxLevel))
        }

    val maxPossibleReward: Int get() = maxUnitsDemanded * reward.rewardPerUnit

    val isValid: Boolean
        get() = reward.isValid
                && (targetItems.any { it != Items.AIR }
                || targetTags.any()
                || targetBlockTags.any()
                || targetConditions.any())

    override fun countConsumableUnits(items: NonNullList<ItemStack>): Int =
        min(super.countConsumableUnits(items), unitsDemanded - unitsFulfilled)

    override fun tryUpdateTick(tag: ContractTag): Boolean {
        if (!isActive) {
            return false
        }

        if (!isInitialized) {
            initialize(tag)
        }

        if (cycleDurationMs <= 0) {
            if (!isComplete) {
                return false
            }
            onContractFulfilled(tag)
            isActive = false
            tag.isActive = isActive
            return true
        }

        if (cyclesPassed > 0) {
            renew(tag, cyclesPassed, newCycleStart)
            return true
        }

        return false
    }

    override fun onContractFulfilled(tag: ContractTag) {
        super.onContractFulfilled(tag)
        if (level < maxLevel) {
            level += 1
            tag.level = level
        }
        if (decayProgress != 0) {
            decayProgress = 0
            tag.decayProgress = 0
        }
    }

    private fun applyDecayHit(currentLevel: Int): Int = when (decayFunction) {
        DecayFunctionOptions.FIXED -> max(decayMinLevel, currentLevel - decayLevelsPerEvent)
        DecayFunctionOptions.PERCENTAGE -> {
            if (decayPercentPerEvent <= 0.0) currentLevel
            else max(decayMinLevel, min(currentLevel - 1, floor(currentLevel * (1.0 - decayPercentPerEvent)).toInt()))
        }
    }

    val wouldExpireOnNextDecay: Boolean
        get() = decayEnabled
                && decayMinLevel == 0
                && decayCyclesPerEvent > 0
                && level > 0
                && !isComplete
                && decayProgress + 1 >= decayCyclesPerEvent
                && applyDecayHit(level) <= 0

    override fun renew(tag: ContractTag, cyclesPassed: Int, newCycleStart: Long) {
        if (isInitialized && expiresIn > 0) {
            expiresIn = max(0, expiresIn - cyclesPassed)
            tag.expiresIn = expiresIn
            if (expiresIn == 0) {
                isActive = false
                tag.isActive = isActive
            }
        }

        if (isComplete) {
            onContractFulfilled(tag)
        } else if (isActive && decayEnabled && isInitialized && decayCyclesPerEvent > 0 && hasEffectiveDecay) {
            decayProgress += cyclesPassed
            while (decayProgress >= decayCyclesPerEvent && level > decayMinLevel) {
                decayProgress -= decayCyclesPerEvent
                level = applyDecayHit(level)
            }
            // If the floor halted the loop, drop any further queued progress —
            // otherwise it would stockpile and instantly re-decay if the contract levels up later.
            if (level <= decayMinLevel && decayProgress >= decayCyclesPerEvent) {
                decayProgress = 0
            }
            tag.decayProgress = decayProgress
            tag.level = level
            if (level <= 0) {
                isActive = false
                tag.isActive = isActive
            }
        }

        currentCycleStart = newCycleStart
        tag.currentCycleStart = currentCycleStart
        unitsFulfilled = 0
        tag.unitsFulfilled = unitsFulfilled
    }

    fun initialize(tag: ContractTag? = null) {
        isInitialized = true
        tag?.isInitialized = true
        startTime = System.currentTimeMillis()
        tag?.startTime = startTime
        currentCycleStart = startTime
        tag?.currentCycleStart = currentCycleStart
    }

    override fun tryConsumeFromItems(tag: ContractTag, portal: ContractPortalBlockEntity): ConsumeResult {
        val serverLevel = portal.level as? ServerLevel ?: return ConsumeResult.NONE
        val unitCount = countConsumableUnits(portal.cachedInput.items)
        if (unitCount == 0 || expiresIn == 0) {
            return ConsumeResult.NONE
        }

        val consumedUnits = consumeUnits(unitCount, portal)
        SpigotLinker.get(serverLevel).spitItems(consumedUnits)

        recordFulfilment(unitCount, tag)

        unitsFulfilled += unitCount
        tag.unitsFulfilled = unitsFulfilled

        val ctx = RewardContext(
            level = serverLevel,
            executor = serverLevel.getPlayerByUUID(portal.lastPlayer) as? ServerPlayer,
            pos = Vec3.atBottomCenterOf(portal.blockPos),
        )
        val outcome = reward.apply(unitCount, ctx)
        return ConsumeResult(outcome.items, unitCount, outcome.scoreboardValue)
    }

    fun formatReward(count: Int): String = reward.formatReward(count)

    fun getCycleInfo(): MutableList<Component> {
        val components = mutableListOf<Component>()

        if (isDisabled) {
            components.add(translateContract("disabled").withStyle(ChatFormatting.GRAY))
            return components
        }

        val start = if (isInitialized) currentCycleStart else System.currentTimeMillis()
        val nextCycleStart = start + cycleDurationMs
        val timeRemaining = nextCycleStart - System.currentTimeMillis()
        val timeRemainingString = DenominationsHelper.denominateDurationToString(timeRemaining)
        val timeRemainingColor = getTimeRemainingColor(timeRemaining)

        if (Date(nextCycleStart) <= Date()) {
            components.add(translateContract("cycle_complete").withStyle(ChatFormatting.DARK_PURPLE))
        } else {
            val cycleRemainingComponent =
                if (isComplete) translateContract("cycle_remaining_level_up").withStyle(ChatFormatting.AQUA)
                else translateContract("cycle_remaining").withStyle(timeRemainingColor)
            components.add(cycleRemainingComponent)
            components.add(Component.literal("  $timeRemainingString").withStyle(timeRemainingColor))
        }

        if (expiresIn > 0) {
            components.add(translateContract("expires_in", expiresIn).withStyle(ChatFormatting.DARK_PURPLE))
        }

        if (decayEnabled && decayCyclesPerEvent > 0 && hasEffectiveDecay && !isComplete) {
            components.add(decayInfoComponent())
        }

        return components
    }

    private fun decayInfoComponent(): Component {
        if (wouldExpireOnNextDecay) {
            return translateContract("decay_imminent").withStyle(ChatFormatting.DARK_RED)
        }

        val isFixedSingleEachCycle = decayFunction == DecayFunctionOptions.FIXED
                && decayCyclesPerEvent == 1
                && decayLevelsPerEvent == 1
        if (isFixedSingleEachCycle) {
            return translateContract("decay_basic").withStyle(ChatFormatting.RED)
        }

        val hitDescription = when (decayFunction) {
            DecayFunctionOptions.FIXED ->
                if (decayLevelsPerEvent == 1) translateContract("decay_hit_level_one").string
                else translateContract("decay_hit_levels", decayLevelsPerEvent).string
            DecayFunctionOptions.PERCENTAGE ->
                translateContract("decay_hit_percent", (decayPercentPerEvent * 100).roundToInt()).string
        }
        val cadence = if (decayCyclesPerEvent == 1) translateContract("decay_cycle_one").string
        else translateContract("decay_cycles", decayCyclesPerEvent).string
        return translateContract("decay_paced", hitDescription, cadence).withStyle(ChatFormatting.RED)
    }

    override fun save(nbt: CompoundTag): ContractTag {
        val tag = super.save(nbt)

        // Cycle timing
        tag.currentCycleStart = currentCycleStart
        tag.cycleDurationMs = cycleDurationMs
        tag.expiresIn = expiresIn

        // Demand & fulfillment
        tag.baseUnitsDemanded = baseUnitsDemanded
        tag.unitsFulfilled = unitsFulfilled

        // Reward
        tag.reward = reward.toTagReward()

        // Leveling
        tag.level = level
        tag.quantityGrowthFactor = quantityGrowthFactor
        tag.maxLevel = maxLevel

        // Decay
        tag.decayEnabled = decayEnabled
        tag.decayCyclesPerEvent = decayCyclesPerEvent
        tag.decayLevelsPerEvent = decayLevelsPerEvent
        tag.decayPercentPerEvent = decayPercentPerEvent
        tag.decayMinLevel = decayMinLevel
        tag.decayProgress = decayProgress
        tag.decayFunction = decayFunctionOverride

        // State
        tag.isInitialized = isInitialized

        return tag
    }

    companion object {
        var (ContractTag).reward by reward()

        // Leveling
        var (ContractTag).level by int()
        var (ContractTag).quantityGrowthFactor by double()
        var (ContractTag).maxLevel by int()

        // Decay
        var (ContractTag).decayEnabled by boolean()
        var (ContractTag).decayCyclesPerEvent by int()
        var (ContractTag).decayLevelsPerEvent by int()
        var (ContractTag).decayPercentPerEvent by double()
        var (ContractTag).decayMinLevel by int()
        var (ContractTag).decayProgress by int()
        var (ContractTag).decayFunction by enum<DecayFunctionOptions>()

        // Cycle timing
        var (ContractTag).currentCycleStart by long()
        var (ContractTag).cycleDurationMs by long()
        var (ContractTag).expiresIn by int()

        // Demand & fulfillment
        var (ContractTag).baseUnitsDemanded by int()
        var (ContractTag).unitsFulfilled by int()

        var (ContractTag).isInitialized by boolean()
    }
}
