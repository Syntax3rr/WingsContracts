package dev.biserman.wingscontracts.core

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.compat.computercraft.DetailsHelper.details
import dev.biserman.wingscontracts.config.DecayFunctionOptions
import dev.biserman.wingscontracts.config.GrowthFunctionOptions
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ItemCondition
import dev.biserman.wingscontracts.nbt.Reward
import dev.biserman.wingscontracts.registry.ModItemRegistry
import dev.biserman.wingscontracts.util.DenominationsHelper
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import java.util.*
import kotlin.reflect.full.memberProperties

class AbyssalContract(
    // Identity & targeting
    id: UUID,
    targetItems: List<Item>,
    targetTags: List<TagKey<Item>>,
    targetBlockTags: List<TagKey<Block>>,
    targetConditions: List<ItemCondition>,

    // Cycle timing
    startTime: Long,
    currentCycleStart: Long,
    cycleDurationMs: Long,

    // Demand & fulfillment
    countPerUnit: Int,
    baseUnitsDemanded: Int,
    unitsFulfilled: Int,
    unitsFulfilledEver: Long,
    expiresIn: Int,

    // Display metadata
    author: String,
    name: String?,
    description: String?,
    shortTargetList: String?,
    displayItem: ItemStack?,
    rarity: Int?,

    reward: ContractReward,

    // Leveling
    level: Int,
    quantityGrowthFactor: Double,
    maxLevel: Int,

    // Decay
    decayEnabled: Boolean,
    decayCyclesPerEvent: Int,
    decayLevelsPerEvent: Int,
    decayPercentPerEvent: Double,
    decayMinLevel: Int,
    decayProgress: Int,
    decayFunctionOverride: DecayFunctionOptions?,

    // State
    isActive: Boolean,
    maxLifetimeUnits: Int,
    isInitialized: Boolean,

    currencyAnchor: Item? = null,
) : ServerContract(
    id,
    targetItems,
    targetTags,
    targetBlockTags,
    targetConditions,
    startTime,
    currentCycleStart,
    cycleDurationMs,
    countPerUnit,
    baseUnitsDemanded,
    unitsFulfilled,
    unitsFulfilledEver,
    expiresIn,
    author,
    name,
    description,
    shortTargetList,
    displayItem,
    rarity,
    reward,
    level,
    quantityGrowthFactor,
    maxLevel,
    decayEnabled,
    decayCyclesPerEvent,
    decayLevelsPerEvent,
    decayPercentPerEvent,
    decayMinLevel,
    decayProgress,
    decayFunctionOverride,
    isActive,
    maxLifetimeUnits,
    isInitialized,
    currencyAnchor,
) {
    override val type get() = ContractType.ABYSSAL
    override val item: Item get() = ModItemRegistry.ABYSSAL_CONTRACT.get()
    override val growthFunction: GrowthFunctionOptions
        get() = ModConfig.SERVER.abyssalContractGrowthFunction.get()
    override val defaultDecayFunction: DecayFunctionOptions
        get() = ModConfig.SERVER.abyssalContractDecayFunction.get()

    override fun getDisplayName(rarity: Int): MutableComponent {
        val rarityString = Component.translatable("${WingsContractsMod.MOD_ID}.rarity.${rarity}").string
        val nameString = Component.translatable(name ?: targetName).string
        val effectiveLevel = if (willCapBeforeLevelUp && maxLevel >= 1) maxLevel else level
        val numeralString = Component.translatable("enchantment.level.$effectiveLevel").string

        return Component.translatable(
            "item.${WingsContractsMod.MOD_ID}.contract.abyssal",
            rarityString,
            nameString,
            if (effectiveLevel > 1) numeralString else ""
        )
    }

    override fun getBasicInfo(list: MutableList<Component>?): MutableList<Component> {
        val components = list ?: mutableListOf()

        val rewardsComponent = translateContract(
            "abyssal.rewards",
            reward.formatReward(reward.rewardPerUnit),
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
                reward.formatReward(unitsDemanded * reward.rewardPerUnit),
            ).withStyle(ChatFormatting.LIGHT_PURPLE)
        )

        components.add(
            translateContract(
                "abyssal.max_reward",
                if (maxLevel <= 0) "∞" else maxLevel.toString(),
                reward.formatReward(maxPossibleReward)
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
        reward.formatReward(reward.rewardPerUnit),
        unitsFulfilled,
        unitsDemanded
    ).withStyle(ChatFormatting.DARK_PURPLE)

    override fun calculateRarity(data: ContractSavedData, rewardUnitValue: Double): Int {
        return data.rarityThresholds.indexOfLast { maxUnitsDemanded * rewardUnitValue > it } + 1
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

    override val details
        get() = AbyssalContract::class.memberProperties
            .filter { it.name != "details" }
            .associate { prop ->
                Pair(
                    prop.name, when (prop.name) {
                        "targetItems" -> targetItems.map { it.defaultInstance.details }
                        "targetTags" -> targetTags.map { "#${it.location}" }
                        "targetBlockTags" -> targetBlockTags.map { "#${it.location}" }
                        "reward" -> when (val r = reward) {
                            is ContractReward.Items -> r.stack.details
                            is ContractReward.Commands -> mapOf(
                                "commands" to r.commands,
                                "label" to r.label,
                                "value" to r.value,
                            )
                        }
                        else -> prop.get(this)
                    })
            }.toMutableMap()

    companion object {
        private fun warnAndClampInt(name: String, raw: Int, min: Int, max: Int, write: (Int) -> Unit): Int {
            val clamped = raw.coerceIn(min, max)
            if (clamped != raw) {
                WingsContractsMod.LOGGER.warn(
                    "Contract field '$name'=$raw is out of range [$min..$max]; clamping to $clamped"
                )
                write(clamped)
            }
            return clamped
        }

        private fun warnAndClampDouble(
            name: String, raw: Double, min: Double, max: Double, write: (Double) -> Unit
        ): Double {
            val clamped = raw.coerceIn(min, max)
            if (clamped != raw) {
                WingsContractsMod.LOGGER.warn(
                    "Contract field '$name'=$raw is out of range [$min..$max]; clamping to $clamped"
                )
                write(clamped)
            }
            return clamped
        }

        fun load(tag: ContractTag, data: ContractSavedData? = null): AbyssalContract {
            val reward = tag.reward ?: Reward.Random(1.0)
            return AbyssalContract(
                // Identity & targeting
                id = tag.id ?: UUID.randomUUID(),
                targetItems = tag.targetItems ?: listOf(),
                targetTags = tag.targetTags ?: listOf(),
                targetBlockTags = tag.targetBlockTags ?: listOf(),
                targetConditions = tag.targetConditions ?: listOf(),

                // Cycle timing
                startTime = tag.startTime ?: System.currentTimeMillis(),
                currentCycleStart = tag.currentCycleStart ?: System.currentTimeMillis(),
                cycleDurationMs = tag.cycleDurationMs ?: ModConfig.SERVER.defaultCycleDurationMs.get(),

                // Demand & fulfillment
                countPerUnit = tag.countPerUnit ?: 64,
                baseUnitsDemanded = tag.baseUnitsDemanded ?: 64,
                unitsFulfilled = tag.unitsFulfilled ?: 0,
                unitsFulfilledEver = tag.unitsFulfilledEver ?: 0,
                expiresIn = tag.expiresIn ?: ModConfig.SERVER.defaultExpiresIn.get(),

                // Display metadata
                author = tag.author ?: ModConfig.SERVER.defaultAuthor.get(),
                name = tag.name,
                description = tag.description,
                shortTargetList = tag.shortTargetList,
                displayItem = tag.displayItem,
                rarity = tag.rarity,

                reward = when (reward) {
                    is Reward.Defined -> ContractReward.Items(reward.itemStack)
                    is Reward.Random -> ContractReward.Items(
                        data?.generator?.getRandomReward(reward.value) ?: ContractSavedData.FALLBACK_REWARD.item
                    )
                    is Reward.Commands -> ContractReward.Commands(reward.commands, reward.label, reward.value)
                },

                // Leveling
                level = tag.level ?: 1,
                quantityGrowthFactor = tag.quantityGrowthFactor
                    ?: ModConfig.SERVER.defaultQuantityGrowthFactor.get(),
                maxLevel = tag.maxLevel ?: ModConfig.SERVER.defaultMaxLevel.get(),

                // Decay
                decayEnabled = tag.decayEnabled ?: ModConfig.SERVER.defaultDecayEnabled.get(),
                decayCyclesPerEvent = warnAndClampInt(
                    "decayCyclesPerEvent",
                    tag.decayCyclesPerEvent ?: ModConfig.SERVER.defaultDecayCyclesPerEvent.get(),
                    0, Int.MAX_VALUE,
                ) { tag.decayCyclesPerEvent = it },
                decayLevelsPerEvent = warnAndClampInt(
                    "decayLevelsPerEvent",
                    tag.decayLevelsPerEvent ?: ModConfig.SERVER.defaultDecayLevelsPerEvent.get(),
                    0, Int.MAX_VALUE,
                ) { tag.decayLevelsPerEvent = it },
                decayPercentPerEvent = warnAndClampDouble(
                    "decayPercentPerEvent",
                    tag.decayPercentPerEvent ?: ModConfig.SERVER.defaultDecayPercentPerEvent.get(),
                    0.0, 1.0,
                ) { tag.decayPercentPerEvent = it },
                decayMinLevel = warnAndClampInt(
                    "decayMinLevel",
                    tag.decayMinLevel ?: ModConfig.SERVER.defaultDecayMinLevel.get(),
                    0, Int.MAX_VALUE,
                ) { tag.decayMinLevel = it },
                decayProgress = (tag.decayProgress ?: 0).coerceAtLeast(0),
                decayFunctionOverride = tag.decayFunction,

                // State
                isActive = tag.isActive ?: true,
                maxLifetimeUnits = tag.maxLifetimeUnits ?: ModConfig.SERVER.defaultMaxLifetimeUnits.get(),
                isInitialized = tag.isInitialized ?: false,

                currencyAnchor = tag.currencyAnchorItem(),
            )
        }
    }
}
