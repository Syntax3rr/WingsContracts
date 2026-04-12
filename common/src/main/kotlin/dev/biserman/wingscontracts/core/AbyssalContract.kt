package dev.biserman.wingscontracts.core

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.compat.computercraft.DetailsHelper.details
import dev.biserman.wingscontracts.config.GrowthFunctionOptions
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.config.getOrDefault
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
import dev.biserman.wingscontracts.registry.ModItemRegistry
import dev.biserman.wingscontracts.util.ComponentHelper.trimBrackets
import dev.biserman.wingscontracts.util.DenominationsHelper
import net.minecraft.ChatFormatting
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
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
import kotlin.reflect.full.memberProperties

@Suppress("NullableBooleanElvis")
class AbyssalContract(
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

    var isActive: Boolean,
    var isInitialized: Boolean
) : Contract(
    1,
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
    rarity
) {
    override val item: Item get() = ModItemRegistry.ABYSSAL_CONTRACT.get()
    override fun getDisplayName(rarity: Int): MutableComponent {
        val rarityString = Component.translatable("${WingsContractsMod.MOD_ID}.rarity.${rarity}").string
        val nameString = Component.translatable(name ?: targetName).string
        val numeralString = Component.translatable("enchantment.level.$level").string

        return Component.translatable(
            "item.${WingsContractsMod.MOD_ID}.contract.abyssal",
            rarityString,
            nameString,
            if (level > 1) numeralString else ""
        )
    }

    override fun getBasicInfo(list: MutableList<Component>?): MutableList<Component> {
        val components = list ?: mutableListOf()

        val rewardsComponent = translateContract(
            "abyssal.rewards",
            formatReward(reward.count),
            countPerUnit,
        ).withStyle(ChatFormatting.DARK_PURPLE)

        val targetsList = getTargetListComponents(displayShort = false)
        if (targetsList.size <= 2) {
            components.add(targetsList.fold(rewardsComponent.append(CommonComponents.SPACE)) { acc, entry ->
                acc.append(entry.withStyle(ChatFormatting.DARK_PURPLE))
            })
        } else {
            components.add(
                rewardsComponent
                    .append(CommonComponents.SPACE)
                    .append(targetsList[0].withStyle(ChatFormatting.DARK_PURPLE))
            )
            components.addAll(targetsList.drop(1).map { it.withStyle(ChatFormatting.DARK_PURPLE) })
        }

        if (!description.isNullOrBlank()) {
            components.add(Component.translatable(description).withStyle(ChatFormatting.GRAY))
        }

        components.add(
            translateContract(
                "abyssal.max_reward_cycle",
                formatReward(unitsDemanded * reward.count),
            ).withStyle(ChatFormatting.LIGHT_PURPLE)
        )

        components.add(
            translateContract(
                "abyssal.max_reward",
                if (maxLevel <= 0) "∞" else maxLevel.toString(),
                formatReward(maxPossibleReward)
            ).withStyle(ChatFormatting.DARK_PURPLE)
        )

        components.add(
            translateContract(
                "units_fulfilled",
                unitsFulfilled,
                unitsDemanded,
                unitsFulfilled * countPerUnit,
                unitsDemanded * countPerUnit,
            ).withStyle(ChatFormatting.LIGHT_PURPLE)
        )

        return super.getBasicInfo(components)
    }

    override fun getShortInfo(): Component = translateContract(
        "abyssal.short",
        countPerUnit,
        getTargetListComponents(displayShort = true).joinToString("|") { it.string },
        formatReward(reward.count),
        unitsFulfilled,
        unitsDemanded
    ).withStyle(ChatFormatting.DARK_PURPLE)

    fun getCycleInfo(): MutableList<Component> {
        val components = mutableListOf<Component>()
        val start = if (isInitialized) currentCycleStart else System.currentTimeMillis()
        val nextCycleStart = start + cycleDurationMs
        val timeRemaining = nextCycleStart - System.currentTimeMillis()
        val timeRemainingString = DenominationsHelper.denominateDurationToString(timeRemaining)

        val timeRemainingColor = getTimeRemainingColor(timeRemaining)

        if (isDisabled) {
            components.add(translateContract("disabled").withStyle(ChatFormatting.GRAY))
            return components
        }

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

    override val isDisabled get() = !isActive

    val unitsDemanded: Int get() = unitsDemandedAtLevel(level)

    fun unitsDemandedAtLevel(level: Int): Int {
        if (countPerUnit == 0) {
            return 0
        }

        return when (val growthFn = ModConfig.SERVER.abyssalContractGrowthFunction.get()) {
            GrowthFunctionOptions.LINEAR -> {
                val growth = (baseUnitsDemanded * (level - 1) * (quantityGrowthFactor - 1)).toInt()
                baseUnitsDemanded + growth
            }

            GrowthFunctionOptions.EXPONENTIAL -> (baseUnitsDemanded * quantityGrowthFactor.pow(level - 1)).toInt()
            else -> throw Error("Unrecognized contract growth function: $growthFn")
        }
    }

    override val rewardPerUnit get() = reward.count

    val cyclesPassed get() = ((System.currentTimeMillis() - currentCycleStart) / cycleDurationMs).toInt()
    val newCycleStart get() = currentCycleStart + cycleDurationMs * cyclesPassed

    override fun tryUpdateTick(tag: ContractTag): Boolean {
        if (!isActive) {
            return false
        }

        if (!isInitialized) {
            initialize(tag)
        }

        // for bound contracts that run forever
        if (cycleDurationMs <= 0) {
            if (isComplete) {
                onContractFulfilled(tag)
                isActive = false
                tag.isActive = isActive
                return true
            } else {
                return false
            }
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

    override fun countConsumableUnits(items: NonNullList<ItemStack>): Int =
        min(super.countConsumableUnits(items), unitsDemanded - unitsFulfilled)

    override fun tryConsumeFromItems(tag: ContractTag, portal: ContractPortalBlockEntity): List<ItemStack> {
        val unitCount = countConsumableUnits(portal.cachedInput.items)
        if (unitCount == 0 || expiresIn == 0) {
            return listOf()
        }

        val consumedUnits = consumeUnits(unitCount, portal)
        SpigotLinker.get(portal.level ?: return listOf()).spitItems(consumedUnits)

        unitsFulfilledEver += unitCount
        tag.unitsFulfilledEver = unitsFulfilledEver

        unitsFulfilled += unitCount
        tag.unitsFulfilled = unitsFulfilled

        return getRewardsForUnits(unitCount)
    }

    override val isComplete: Boolean
        get() = unitsFulfilled >= unitsDemanded

    fun formatReward(count: Int): String {
        val rewardEntry =
            ContractDataReloadListener.data.defaultRewards.firstOrNull { it.item.item == reward.item }
        if (rewardEntry == null || rewardEntry.formatString == null) {
            val trimmed = reward.displayName.string.trimBrackets()
            when {
                reward.has(DataComponents.STORED_ENCHANTMENTS) -> {
                    val enchantments = reward.get(DataComponents.STORED_ENCHANTMENTS)?.entrySet()?.mapNotNull { kvp ->
                        val resourceLocation = kvp.key.unwrapKey().getOrNull()?.location() ?: return@mapNotNull null
                        val level = kvp.intValue

                        val name =
                            Component.translatable("enchantment.${resourceLocation.namespace}.${resourceLocation.path}").string
                        val levelString = Component.translatable("enchantment.level.$level").string

                        return@mapNotNull "$name $levelString"
                    } ?: listOf()

                    return translateContract(
                        "enchanted_book_format",
                        count,
                        enchantments.joinToString(" + "),
                        trimmed
                    ).string
                }

                reward.isEnchanted -> return translateContract("enchanted_reward_format", count, trimmed).string
                else -> return "$count $trimmed"
            }
        } else {
            return String.format(rewardEntry.formatString, count)
        }
    }

    fun getRewardsForUnits(units: Int): List<ItemStack> {
        val rewardsList = (1..Mth.floor(reward.count * units.toDouble() / reward.maxStackSize)).map {
            reward.copyWithCount(reward.maxStackSize)
        }.toMutableList()

        val remainder = reward.count * units % reward.maxStackSize
        if (remainder != 0) {
            rewardsList.add(reward.copyWithCount(remainder))
        }

        return rewardsList
    }

    fun initialize(tag: ContractTag? = null) {
        isInitialized = true
        tag?.isInitialized = true
        startTime = System.currentTimeMillis()
        tag?.startTime = startTime
        currentCycleStart = startTime
        tag?.currentCycleStart = currentCycleStart
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

    fun calculateRarity(data: ContractSavedData, rewardUnitValue: Double): Int {
        return data.rarityThresholds.indexOfLast { (maxPossibleReward / reward.count) * rewardUnitValue > it } + 1
    }

    override fun addToGoggleTooltip(
        portal: ContractPortalBlockEntity,
        tooltip: MutableList<Component>,
        isPlayerSneaking: Boolean
    ): Boolean {
        tooltip.add(Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.header"))

        if (isDisabled) {
            Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.disabled")
            return true
        }

        val completedComponent = if (isComplete) {
            Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.complete")
        } else {
            Component.literal("$unitsFulfilled / $unitsDemanded")
        }

        tooltip.add(
            Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.progress")
                .withStyle(ChatFormatting.GRAY)
                .append(CommonComponents.SPACE)
                .append(completedComponent.withStyle(ChatFormatting.AQUA))
        )

        val nextCycleStart = currentCycleStart + cycleDurationMs
        val timeRemaining = nextCycleStart - System.currentTimeMillis()
        val timeRemainingString = "     " + DenominationsHelper.denominateDurationToString(timeRemaining)
        val timeRemainingComponent = if (isComplete) {
            Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.remaining_time_level_up")
        } else {
            Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.remaining_time")
        }

        tooltip.add(timeRemainingComponent.withStyle(ChatFormatting.GRAY))
        tooltip.add(Component.literal(timeRemainingString).withStyle(getTimeRemainingColor(timeRemaining)))

        return true
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
        tag.isActive = isActive
        tag.isInitialized = isInitialized

        return tag
    }

    override val details
        get() = AbyssalContract::class.memberProperties
            .filter { it.name != "details" }
            .associate { prop ->
                return@associate Pair(
                    prop.name, when (prop.name) {
                        "targetItems" -> targetItems.map { it.defaultInstance.details }
                        "targetTags" -> targetTags.map { "#${it.location}" }
                        "targetBlockTags" -> targetBlockTags.map { "#${it.location}" }
                        "reward" -> reward.details
                        else -> prop.get(this)
                    })
            }.toMutableMap()

    val isValid
        get() = reward.item != Items.AIR
                && (targetItems.any { it != Items.AIR }
                || targetTags.any()
                || targetBlockTags.any()
                || targetConditions.any())

    companion object {
        var (ContractTag).reward by reward()

        var (ContractTag).level by int()
        var (ContractTag).quantityGrowthFactor by double()
        var (ContractTag).maxLevel by int()

        var (ContractTag).isActive by boolean()

        var (ContractTag).currentCycleStart by long()
        var (ContractTag).cycleDurationMs by long()
        var (ContractTag).expiresIn by int()
        var (ContractTag).baseUnitsDemanded by int()
        var (ContractTag).unitsFulfilled by int()

        var (ContractTag).isInitialized by boolean()

        fun load(tag: ContractTag, data: ContractSavedData? = null): AbyssalContract {
            val reward = tag.reward ?: Reward.Random(1.0)
            return AbyssalContract(
                id = tag.id ?: UUID.randomUUID(),
                targetItems = tag.targetItems ?: listOf(),
                targetTags = tag.targetTags ?: listOf(),
                targetBlockTags = tag.targetBlockTags ?: listOf(),
                targetConditions = tag.targetConditions ?: listOf(),
                startTime = tag.startTime ?: System.currentTimeMillis(),
                currentCycleStart = tag.currentCycleStart ?: System.currentTimeMillis(),
                cycleDurationMs = tag.cycleDurationMs ?: ModConfig.SERVER.defaultCycleDurationMs.get(),
                countPerUnit = tag.countPerUnit ?: 64,
                baseUnitsDemanded = tag.baseUnitsDemanded ?: 64,
                unitsFulfilled = tag.unitsFulfilled ?: 0,
                unitsFulfilledEver = tag.unitsFulfilledEver ?: 0,
                expiresIn = tag.expiresIn ?: ModConfig.SERVER.defaultExpiresIn.get(),
                author = tag.author ?: ModConfig.SERVER.defaultAuthor.get(),
                name = tag.name,
                description = tag.description,
                shortTargetList = tag.shortTargetList,
                displayItem = tag.displayItem,
                rarity = tag.rarity,
                reward = when (reward) {
                    is Reward.Defined -> reward.itemStack
                    is Reward.Random ->
                        data?.generator?.getRandomReward(reward.value) ?: ContractSavedData.FALLBACK_REWARD.item
                },
                level = tag.level ?: 1,
                quantityGrowthFactor = tag.quantityGrowthFactor
                    ?: ModConfig.SERVER.defaultQuantityGrowthFactor.get(),
                maxLevel = tag.maxLevel ?: ModConfig.SERVER.defaultMaxLevel.get(),
                isActive = tag.isActive ?: true,
                isInitialized = tag.isInitialized ?: false
            )
        }
    }
}