package dev.biserman.wingscontracts.core

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.compat.computercraft.DetailsHelper.details
import dev.biserman.wingscontracts.config.DecayFunctionOptions
import dev.biserman.wingscontracts.config.GrowthFunctionOptions
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.core.Contract.Companion.currencyAnchorItem
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ItemCondition
import dev.biserman.wingscontracts.nbt.Reward
import dev.biserman.wingscontracts.registry.ModItemRegistry
import dev.biserman.wingscontracts.util.DenominationsHelper
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import java.util.*
import kotlin.reflect.full.memberProperties

class CelestialContract(
    id: UUID,
    targetItems: List<Item>,
    targetTags: List<TagKey<Item>>,
    targetBlockTags: List<TagKey<Block>>,
    targetConditions: List<ItemCondition>,

    startTime: Long,
    currentCycleStart: Long,
    cycleDurationMs: Long,

    countPerUnit: Int,
    baseUnitsDemanded: Int,
    unitsFulfilled: Int,
    unitsFulfilledEver: Long,
    expiresIn: Int,

    author: String,
    name: String?,
    description: String?,
    shortTargetList: String?,
    displayItem: ItemStack?,
    rarity: Int?,

    reward: ContractReward,

    level: Int,
    quantityGrowthFactor: Double,
    maxLevel: Int,

    decayEnabled: Boolean,
    decayCyclesPerEvent: Int,
    decayLevelsPerEvent: Int,
    decayPercentPerEvent: Double,
    decayMinLevel: Int,
    decayProgress: Int,
    decayFunctionOverride: DecayFunctionOptions?,

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
    override val type get() = ContractType.CELESTIAL
    override val item: Item get() = ModItemRegistry.CELESTIAL_CONTRACT.get()
    override val growthFunction: GrowthFunctionOptions
        get() = ModConfig.SERVER.celestialContractGrowthFunction.get()
    override val defaultDecayFunction: DecayFunctionOptions
        get() = ModConfig.SERVER.abyssalContractDecayFunction.get()

    override val primaryStyle: ChatFormatting = ChatFormatting.GOLD
    override val accentStyle: ChatFormatting = ChatFormatting.AQUA

    override val targetDisplayItem: ItemStack? get() = null

    override val displayItems by lazy {
        if (displayItem != null) {
            listOf(displayItem)
        } else when (val r = reward) {
            is ContractReward.Items -> if (r.stack.isEmpty) {
                listOf(ModItemRegistry.STAR.get()?.defaultInstance ?: ItemStack.EMPTY)
            } else {
                listOf(r.stack)
            }
            is ContractReward.Commands ->
                listOf(ModItemRegistry.STAR.get()?.defaultInstance ?: ItemStack.EMPTY)
        }
    }

    override val portalDisplayItems: List<ItemStack> by lazy {
        val targetIcons = targetItems.map { it.defaultInstance }
            .plus(targetTags.flatMap {
                BuiltInRegistries.ITEM.getTagOrEmpty(it)
                    .map { holder -> holder.value().defaultInstance }
            })
            .plus(targetBlockTags.flatMap {
                BuiltInRegistries.BLOCK.getTagOrEmpty(it)
                    .map { holder -> holder.value().asItem().defaultInstance }
            })
        if (targetIcons.isNotEmpty()) {
            targetIcons
        } else {
            currencyAnchor?.let { listOf(it.defaultInstance) }
                ?: listOf(ModItemRegistry.QUESTION_MARK.get()?.defaultInstance ?: ItemStack.EMPTY)
        }
    }

    override fun getDisplayName(rarity: Int): MutableComponent {
        val rarityString = Component.translatable("${WingsContractsMod.MOD_ID}.rarity.${rarity}").string
        val nameString = Component.translatable(name ?: targetName).string
        val effectiveLevel = if (willCapBeforeLevelUp && maxLevel >= 1) maxLevel else level
        val numeralString = Component.translatable("enchantment.level.$effectiveLevel").string

        return Component.translatable(
            "item.${WingsContractsMod.MOD_ID}.contract.celestial",
            rarityString,
            nameString,
            if (effectiveLevel > 1) numeralString else ""
        )
    }

    override fun getBasicInfo(list: MutableList<Component>?): MutableList<Component> {
        val components = list ?: mutableListOf()

        val rewardsComponent = translateContract(
            "celestial.rewards",
            reward.formatReward(reward.rewardPerUnit),
            countPerUnit,
        ).withStyle(ChatFormatting.GOLD)

        val targetsList = getTargetListComponents(displayShort = false)
        if (targetsList.size <= 2) {
            components.add(targetsList.fold(rewardsComponent.append(CommonComponents.SPACE)) { acc, entry ->
                acc.append(entry.withStyle(ChatFormatting.GOLD))
            })
        } else {
            components.add(
                rewardsComponent
                    .append(CommonComponents.SPACE)
                    .append(targetsList[0].withStyle(ChatFormatting.GOLD))
            )
            components.addAll(targetsList.drop(1).map { it.withStyle(ChatFormatting.GOLD) })
        }

        if (!description.isNullOrBlank()) {
            components.add(Component.translatable(description).withStyle(ChatFormatting.GRAY))
        }

        if (cycleDurationMs > 0) {
            components.add(
                translateContract(
                    "celestial.max_reward_cycle",
                    reward.formatReward(unitsDemanded * reward.rewardPerUnit),
                ).withStyle(ChatFormatting.AQUA)
            )
        }

        val hasFiniteExpiry = cycleDurationMs > 0 && expiresIn > 0
        if (!hasFiniteExpiry) {
            val line = if (maxLifetimeUnits <= 0) {
                translateContract("celestial.uses_unlimited")
            } else {
                val remaining = (maxLifetimeUnits.toLong() - unitsFulfilledEver).coerceAtLeast(0L)
                if (remaining == 1L) translateContract("celestial.uses_remaining_one")
                else translateContract("celestial.uses_remaining", remaining)
            }
            components.add(line.withStyle(ChatFormatting.GOLD))
        }

        if (cycleDurationMs > 0) {
            components.add(
                translateContract(
                    "units_fulfilled",
                    unitsFulfilled,
                    unitsDemanded,
                    unitsFulfilled * countPerUnit,
                    unitsDemanded * countPerUnit,
                ).withStyle(ChatFormatting.AQUA)
            )
        }

        return super.getBasicInfo(components)
    }

    override fun getShortInfo(): Component = translateContract(
        "celestial.short",
        countPerUnit,
        getTargetListComponents(displayShort = true).joinToString("|") { it.string },
        reward.formatReward(reward.rewardPerUnit),
        unitsFulfilledEver,
        if (maxLifetimeUnits <= 0) "∞" else maxLifetimeUnits.toString(),
    ).withStyle(ChatFormatting.GOLD)

    override fun calculateRarity(data: ContractSavedData, rewardUnitValue: Double): Int {
        // Rarity tracks total committed payout. Unlimited contracts use a sentinel cap to keep the math finite.
        val cap = ModConfig.SERVER.celestialUnlimitedLifetimeUnitsRarityCap.get()
        val effectiveLifetimeUnits = if (maxLifetimeUnits <= 0) cap else maxLifetimeUnits
        val totalValue = effectiveLifetimeUnits.toDouble() * reward.rewardPerUnit * rewardUnitValue
        return data.rarityThresholds.indexOfLast { totalValue > it } + 1
    }

    override fun addToGoggleTooltip(
        portal: ContractPortalBlockEntity,
        tooltip: MutableList<Component>,
        isPlayerSneaking: Boolean
    ): Boolean {
        tooltip.add(Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.header"))

        if (isDisabled) {
            tooltip.add(Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.disabled"))
            return true
        }

        val progressLabel = if (cycleDurationMs > 0) {
            if (isComplete) Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.complete")
            else Component.literal("$unitsFulfilled / $unitsDemanded")
        } else {
            val cap = if (maxLifetimeUnits <= 0) "∞" else maxLifetimeUnits.toString()
            Component.literal("$unitsFulfilledEver / $cap")
        }
        tooltip.add(
            Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.progress")
                .withStyle(ChatFormatting.GRAY)
                .append(CommonComponents.SPACE)
                .append(progressLabel.withStyle(ChatFormatting.GOLD))
        )

        if (cycleDurationMs > 0) {
            val nextCycleStart = currentCycleStart + cycleDurationMs
            val timeRemaining = nextCycleStart - System.currentTimeMillis()
            val timeRemainingString = "     " + DenominationsHelper.denominateDurationToString(timeRemaining)
            val timeRemainingComponent = if (isComplete) {
                Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.remaining_time_level_up")
            } else {
                Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.remaining_time")
            }
            tooltip.add(timeRemainingComponent.withStyle(ChatFormatting.GRAY))
            tooltip.add(Component.literal(timeRemainingString).withStyle(timeRemainingColor(timeRemaining)))
        }

        return true
    }

    override val details
        get() = CelestialContract::class.memberProperties
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
        const val FALLBACK_CYCLE_DURATION_MS: Long = 0L
        const val FALLBACK_MAX_LIFETIME_UNITS: Int = 1
        const val FALLBACK_MAX_LEVEL: Int = 1
        const val FALLBACK_QUANTITY_GROWTH_FACTOR: Double = 1.0
        const val FALLBACK_EXPIRES_IN: Int = -1
        const val FALLBACK_AUTHOR: String = "${WingsContractsMod.MOD_ID}.default_celestial_author"

        fun maxAchievableLifetimeUnits(
            cycleDurationMs: Long,
            expiresIn: Int,
            maxLevel: Int,
            quantityGrowthFactor: Double,
            growthFunction: GrowthFunctionOptions,
            baseUnitsDemanded: Int = 1,
        ): Int? {
            if (cycleDurationMs <= 0L) return null
            if (expiresIn < 0) return null
            if (expiresIn == 0) return 0
            val effectiveMaxLevel = maxLevel.coerceAtLeast(1)
            var total = 0L
            for (cycle in 1..expiresIn) {
                val level = minOf(cycle, effectiveMaxLevel)
                val units = ServerContract.unitsAt(level, baseUnitsDemanded, quantityGrowthFactor, growthFunction)
                    .coerceAtLeast(1)
                total += units
                if (total >= Int.MAX_VALUE) return Int.MAX_VALUE
            }
            return total.toInt()
        }

        fun clampMaxLifetimeUnits(rawMax: Int, achievable: Int?): Int {
            if (achievable == null) return rawMax
            if (rawMax == 0) return achievable.coerceAtLeast(1)
            return minOf(rawMax, achievable).coerceAtLeast(1)
        }

        fun load(tag: ContractTag, data: ContractSavedData? = null): CelestialContract {
            val tagReward = tag.reward ?: Reward.Random(1.0)

            val anchor = tag.currencyAnchorItem() ?: run {
                val targets = tag.targetItems ?: listOf()
                val tags = tag.targetTags ?: listOf()
                val blockTags = tag.targetBlockTags ?: listOf()
                val conditions = tag.targetConditions ?: listOf()
                if (targets.isEmpty() && tags.isEmpty() && blockTags.isEmpty() && conditions.isEmpty()) {
                    // Random celestials ship no targets, so fall back to the global anchor.
                    val key = ModConfig.SERVER.celestialCurrencyAnchor.get()
                    val resolved = ResourceLocation.tryParse(key)?.let { BuiltInRegistries.ITEM[it] }
                    if (resolved == null || resolved == Items.AIR) null else resolved
                } else null
            }

            val cycleDurationMs = tag.cycleDurationMs ?: FALLBACK_CYCLE_DURATION_MS
            val expiresIn = tag.expiresIn ?: FALLBACK_EXPIRES_IN
            val maxLevel = tag.maxLevel ?: FALLBACK_MAX_LEVEL
            val quantityGrowthFactor = tag.quantityGrowthFactor ?: FALLBACK_QUANTITY_GROWTH_FACTOR
            val rawMaxLifetimeUnits = tag.maxLifetimeUnits ?: FALLBACK_MAX_LIFETIME_UNITS

            // Finite expiry caps lifetime units naturally. Clamp `maxLifetimeUnits` so unlimited (0) becomes the achievable cap.
            val achievable = maxAchievableLifetimeUnits(
                cycleDurationMs = cycleDurationMs,
                expiresIn = expiresIn,
                maxLevel = maxLevel,
                quantityGrowthFactor = quantityGrowthFactor,
                growthFunction = ModConfig.SERVER.celestialContractGrowthFunction.get(),
                baseUnitsDemanded = tag.baseUnitsDemanded ?: 1,
            )
            val maxLifetimeUnits = clampMaxLifetimeUnits(rawMaxLifetimeUnits, achievable)

            return CelestialContract(
                id = tag.id ?: UUID.randomUUID(),
                targetItems = tag.targetItems ?: listOf(),
                targetTags = tag.targetTags ?: listOf(),
                targetBlockTags = tag.targetBlockTags ?: listOf(),
                targetConditions = tag.targetConditions ?: listOf(),
                startTime = tag.startTime ?: System.currentTimeMillis(),
                currentCycleStart = tag.currentCycleStart ?: System.currentTimeMillis(),
                cycleDurationMs = cycleDurationMs,
                countPerUnit = tag.countPerUnit ?: 0,
                baseUnitsDemanded = tag.baseUnitsDemanded ?: 1,
                unitsFulfilled = tag.unitsFulfilled ?: 0,
                unitsFulfilledEver = tag.unitsFulfilledEver ?: 0,
                expiresIn = expiresIn,
                author = tag.author ?: FALLBACK_AUTHOR,
                name = tag.name,
                description = tag.description,
                shortTargetList = tag.shortTargetList,
                displayItem = tag.displayItem,
                rarity = tag.rarity,
                reward = when (tagReward) {
                    is Reward.Defined -> ContractReward.Items(tagReward.itemStack)
                    is Reward.Random -> ContractReward.Items(
                        data?.generator?.getRandomReward(tagReward.value) ?: ContractSavedData.FALLBACK_REWARD.item
                    )
                    is Reward.Commands -> ContractReward.Commands(tagReward.commands, tagReward.label, tagReward.value)
                },
                level = tag.level ?: 1,
                quantityGrowthFactor = quantityGrowthFactor,
                maxLevel = maxLevel,
                // Celestials default decay OFF since most are one-shot or non-leveling.
                decayEnabled = tag.decayEnabled ?: false,
                decayCyclesPerEvent = (tag.decayCyclesPerEvent ?: ModConfig.SERVER.defaultDecayCyclesPerEvent.get()).coerceAtLeast(0),
                decayLevelsPerEvent = (tag.decayLevelsPerEvent ?: ModConfig.SERVER.defaultDecayLevelsPerEvent.get()).coerceAtLeast(0),
                decayPercentPerEvent = (tag.decayPercentPerEvent ?: ModConfig.SERVER.defaultDecayPercentPerEvent.get()).coerceIn(0.0, 1.0),
                decayMinLevel = (tag.decayMinLevel ?: ModConfig.SERVER.defaultDecayMinLevel.get()).coerceAtLeast(0),
                decayProgress = (tag.decayProgress ?: 0).coerceAtLeast(0),
                decayFunctionOverride = tag.decayFunction,
                isActive = tag.isActive ?: true,
                maxLifetimeUnits = maxLifetimeUnits,
                isInitialized = tag.isInitialized ?: false,
                currencyAnchor = anchor,
            )
        }
    }
}
