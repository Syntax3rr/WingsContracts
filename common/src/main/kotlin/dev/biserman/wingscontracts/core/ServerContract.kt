package dev.biserman.wingscontracts.core

import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.config.GrowthFunctionOptions
import dev.biserman.wingscontracts.data.ContractDataReloadListener
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ContractTagHelper.boolean
import dev.biserman.wingscontracts.nbt.ContractTagHelper.double
import dev.biserman.wingscontracts.nbt.ContractTagHelper.int
import dev.biserman.wingscontracts.nbt.ContractTagHelper.long
import dev.biserman.wingscontracts.nbt.ContractTagHelper.reward
import dev.biserman.wingscontracts.nbt.ItemCondition
import dev.biserman.wingscontracts.nbt.Reward
import dev.biserman.wingscontracts.util.ComponentHelper.trimBrackets
import dev.biserman.wingscontracts.util.DenominationsHelper
import net.minecraft.ChatFormatting
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import java.util.*
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Suppress("MemberVisibilityCanBePrivate")
abstract class ServerContract(
    id: UUID,
    targetItems: List<Item>,
    targetTags: List<TagKey<Item>>,
    targetBlockTags: List<TagKey<Block>>,
    targetConditions: List<ItemCondition>,

    startTime: Long,
    var currentCycleStart: Long,
    val cycleDurationMs: Long,

    countPerUnit: Int,
    val baseUnitsDemanded: Int,
    var unitsFulfilled: Int,
    unitsFulfilledEver: Long,
    var expiresIn: Int,

    author: String,
    name: String?,
    description: String?,
    shortTargetList: String?,
    displayItem: ItemStack?,
    rarity: Int?,

    val reward: ItemStack,

    var level: Int,
    val quantityGrowthFactor: Double,
    val maxLevel: Int,

    isActive: Boolean,
    maxFulfilments: Int,
    var isInitialized: Boolean
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
) {
    abstract val growthFunction: GrowthFunctionOptions

    abstract fun calculateRarity(data: ContractSavedData, rewardUnitValue: Double): Int

    override val rewardPerUnit get() = reward.count
    override val isComplete get() = unitsFulfilled >= unitsDemanded

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

    val maxPossibleReward: Int
        get() {
            if (maxLevel <= 0) {
                val compare = unitsDemandedAtLevel(1).compareTo(unitsDemandedAtLevel(2))
                return when {
                    compare < 0 -> Int.MAX_VALUE
                    compare == 0 -> unitsDemandedAtLevel(1) * reward.count
                    else -> reward.count
                }
            }
            val maxUnitsDemanded = max(unitsDemandedAtLevel(1), unitsDemandedAtLevel(maxLevel))
            return maxUnitsDemanded * reward.count
        }

    val isValid: Boolean
        get() = reward.item != Items.AIR
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
    }

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

    override fun tryConsumeFromItems(tag: ContractTag, portal: ContractPortalBlockEntity): List<ItemStack> {
        val unitCount = countConsumableUnits(portal.cachedInput.items)
        if (unitCount == 0 || expiresIn == 0) {
            return listOf()
        }

        val consumedUnits = consumeUnits(unitCount, portal)
        SpigotLinker.get(portal.level ?: return listOf()).spitItems(consumedUnits)

        recordFulfilment(unitCount, tag)

        unitsFulfilled += unitCount
        tag.unitsFulfilled = unitsFulfilled

        return getRewardsForUnits(unitCount)
    }

    fun getRewardsForUnits(units: Int): List<ItemStack> {
        val fullStackCount = Mth.floor(reward.count * units.toDouble() / reward.maxStackSize)
        val remainder = reward.count * units % reward.maxStackSize
        val fullStacks = (1..fullStackCount).map { reward.copyWithCount(reward.maxStackSize) }
        return if (remainder == 0) fullStacks else fullStacks + reward.copyWithCount(remainder)
    }

    fun formatReward(count: Int): String {
        val rewardEntry =
            ContractDataReloadListener.data.defaultRewards.firstOrNull { it.item.item == reward.item }
        if (rewardEntry?.formatString != null) {
            return String.format(rewardEntry.formatString, count)
        }

        val trimmed = reward.displayName.string.trimBrackets()
        return when {
            reward.has(DataComponents.STORED_ENCHANTMENTS) -> {
                val enchantments = reward.get(DataComponents.STORED_ENCHANTMENTS)?.entrySet()?.mapNotNull { kvp ->
                    val resourceLocation = kvp.key.unwrapKey().getOrNull()?.location() ?: return@mapNotNull null
                    val enchantmentLevel = kvp.intValue
                    val name =
                        Component.translatable("enchantment.${resourceLocation.namespace}.${resourceLocation.path}").string
                    val levelString = Component.translatable("enchantment.level.$enchantmentLevel").string
                    "$name $levelString"
                } ?: listOf()

                translateContract(
                    "enchanted_book_format",
                    count,
                    enchantments.joinToString(" + "),
                    trimmed
                ).string
            }

            reward.isEnchanted -> translateContract("enchanted_reward_format", count, trimmed).string
            else -> "$count $trimmed"
        }
    }

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

        return components
    }

    override fun save(nbt: CompoundTag): ContractTag {
        val tag = super.save(nbt)

        tag.currentCycleStart = currentCycleStart
        tag.cycleDurationMs = cycleDurationMs
        tag.baseUnitsDemanded = baseUnitsDemanded
        tag.unitsFulfilled = unitsFulfilled
        tag.expiresIn = expiresIn
        tag.reward = Reward.Defined(reward)
        tag.level = level
        tag.quantityGrowthFactor = quantityGrowthFactor
        tag.maxLevel = maxLevel
        tag.isInitialized = isInitialized

        return tag
    }

    companion object {
        var (ContractTag).reward by reward()

        var (ContractTag).level by int()
        var (ContractTag).quantityGrowthFactor by double()
        var (ContractTag).maxLevel by int()

        var (ContractTag).currentCycleStart by long()
        var (ContractTag).cycleDurationMs by long()
        var (ContractTag).expiresIn by int()
        var (ContractTag).baseUnitsDemanded by int()
        var (ContractTag).unitsFulfilled by int()

        var (ContractTag).isInitialized by boolean()
    }
}
