package dev.biserman.wingscontracts.core

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.block.ContractPortalBlock
import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.block.state.properties.ContractPortalMode
import dev.biserman.wingscontracts.compat.computercraft.DetailsHelper.details
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.data.ContractDataReloadListener
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.data.LoadedContracts
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import dev.biserman.wingscontracts.nbt.ContractTagHelper.csv
import dev.biserman.wingscontracts.nbt.ContractTagHelper.int
import dev.biserman.wingscontracts.nbt.ContractTagHelper.uuid
import dev.biserman.wingscontracts.nbt.ItemCondition
import dev.biserman.wingscontracts.registry.ModItemRegistry
import dev.biserman.wingscontracts.util.ComponentHelper.trimBrackets
import dev.biserman.wingscontracts.util.DenominationsHelper
import net.minecraft.ChatFormatting
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import java.util.*
import kotlin.math.floor
import kotlin.math.min
import kotlin.reflect.full.memberProperties

class BoundContract(
    id: UUID,
    targetItems: List<Item>,
    targetTags: List<TagKey<Item>>,
    targetBlockTags: List<TagKey<Block>>,
    targetConditions: List<ItemCondition>,

    val otherSideCountPerUnit: Int,
    val otherSideTargets: List<String>,

    startTime: Long,

    countPerUnit: Int,
    unitsFulfilledEver: Long,

    author: String,
    name: String?,

    val matchingContractId: UUID,

    isActive: Boolean,
    maxLifetimeUnits: Int,

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
    null,
    null,
    null,
    null,
    isActive,
    maxLifetimeUnits,
    currencyAnchor,
) {
    override val type get() = ContractType.BOUND
    override val item: Item get() = ModItemRegistry.BOUND_CONTRACT.get()
    override fun getDisplayName(rarity: Int): MutableComponent {
        val nameString = Component.translatable(name ?: targetName).string

        return Component.translatable(
            "item.${WingsContractsMod.MOD_ID}.contract.bound",
            nameString,
        )
    }

    override fun getShortInfo(): Component {
        val targets = listTargets
        return when {
            targets.size <= 3 && otherSideTargets.size <= 3 -> {
                translateContract(
                    "bound.short",
                    countPerUnit,
                    targets.joinToString("|"),
                    otherSideCountPerUnit,
                    otherSideTargets.joinToString("|") { Component.translatable(it).string.trimBrackets() }
                ).withStyle(ChatFormatting.DARK_PURPLE)
            }

            else -> CommonComponents.EMPTY
        }
    }

    override fun getBasicInfo(list: MutableList<Component>?): MutableList<Component> {
        val components = list ?: mutableListOf()

        components.add(translateContract("bound.exchanges", countPerUnit).withStyle(ChatFormatting.DARK_PURPLE))
        for (target in listTargets) {
            components.add(
                Component.literal(" - ")
                    .withStyle(ChatFormatting.DARK_PURPLE)
                    .append(target)
                    .withStyle((ChatFormatting.LIGHT_PURPLE))
            )
        }

        components.add(translateContract("bound.for", otherSideCountPerUnit).withStyle(ChatFormatting.DARK_PURPLE))
        for (otherTarget in otherSideTargets) {
            components.add(
                Component.literal(" - ")
                    .withStyle(ChatFormatting.DARK_PURPLE)
                    .append(Component.translatable(otherTarget).string.trimBrackets())
                    .withStyle((ChatFormatting.LIGHT_PURPLE))
            )
        }

        if (!description.isNullOrBlank()) {
            components.add(Component.translatable(description).withStyle(ChatFormatting.GRAY))
        }

        return components
    }

    fun getLinkedPortal(level: Level) = PortalLinker.get(level).linkedPortals[matchingContractId]

    override val rewardPerUnit get() = otherSideCountPerUnit

    override fun tryConsumeFromItems(tag: ContractTag, portal: ContractPortalBlockEntity): ConsumeResult {
        if (!isActive) return ConsumeResult.NONE

        val otherPortal = PortalLinker.get(portal.level ?: return ConsumeResult.NONE)
            .linkedPortals[matchingContractId] ?: return ConsumeResult.NONE
        val otherTag = ContractTagHelper.getContractTag(otherPortal.contractSlot) ?: return ConsumeResult.NONE
        val otherContract = LoadedContracts[otherTag] ?: return ConsumeResult.NONE
        val level = portal.level ?: return ConsumeResult.NONE

        if (ModConfig.SERVER.boundContractRequiresTwoPlayers.get() && portal.lastPlayer == otherPortal.lastPlayer) {
            return ConsumeResult.NONE
        }

        if (portal.isPowered || otherPortal.isPowered) {
            return ConsumeResult.NONE
        }

        val unitCount = min(
            countConsumableUnits(portal.cachedInput.items),
            otherContract.countConsumableUnits(otherPortal.cachedInput.items)
        )
        if (unitCount == 0) {
            return ConsumeResult.NONE
        }

        val consumedItems = consumeUnits(unitCount, portal).toMutableList()
        val otherConsumedItems = otherContract.consumeUnits(unitCount, otherPortal).toMutableList()

        burnSomeItems(this, consumedItems, level)
        for (consumedItem in consumedItems) {
            otherPortal.cachedRewards.addItem(consumedItem)
        }

        recordFulfilment(unitCount, tag)
        otherContract.recordFulfilment(unitCount, otherTag)

        burnSomeItems(otherContract, otherConsumedItems, level)
        val score = otherConsumedItems.sumOf { floor(ContractDataReloadListener.data.valueReward(it)) }
        return ConsumeResult(otherConsumedItems, unitCount, score)
    }

    fun burnSomeItems(contract: Contract, items: MutableList<ItemStack>, level: Level) {
        val lossRate = ModConfig.SERVER.boundContractLossRate.get()
        val anchor = contract.currencyAnchor
        val denominations = anchor?.let { ContractSavedData.fakeData.currencyHandler.itemToCurrencyMap[it] }

        if (denominations != null) {
            val totalValue = items.sumOf { (denominations[it.item] ?: 0.0) * it.count }
            val rawBurn = totalValue * lossRate
            var valueToBurn = floor(rawBurn).toLong()
            if (level.random.nextDouble() <= rawBurn % 1.0) {
                valueToBurn += 1
            }
            for (consumedItem in items) {
                if (valueToBurn <= 0) break
                val itemValue = denominations[consumedItem.item]?.toLong() ?: continue
                if (itemValue <= 0) continue
                val toBurnFromThis = min(consumedItem.count.toLong(), valueToBurn / itemValue).toInt()
                if (toBurnFromThis > 0) {
                    valueToBurn -= toBurnFromThis * itemValue
                    consumedItem.split(toBurnFromThis)
                }
            }
            // If only larger denominations remain, split one and replace the surplus with change.
            if (valueToBurn > 0) {
                val breakable = items
                    .filter { !it.isEmpty && (denominations[it.item]?.toLong() ?: 0L) >= valueToBurn }
                    .minByOrNull { denominations[it.item] ?: Double.MAX_VALUE }
                if (breakable != null) {
                    val itemValue = denominations[breakable.item]!!.toLong()
                    breakable.split(1)
                    val change = itemValue - valueToBurn
                    if (change > 0) {
                        for ((item, count) in DenominationsHelper.denominate(change.toDouble(), denominations)) {
                            var leftover = count
                            while (leftover > 0) {
                                val take = min(leftover, item.defaultMaxStackSize)
                                items.add(ItemStack(item, take))
                                leftover -= take
                            }
                        }
                    }
                }
            }
        } else {
            val lostItems = items.sumOf { it.count } * lossRate
            var countToBurn = floor(lostItems).toInt()
            if (level.random.nextDouble() <= lostItems % 1.0) {
                countToBurn += 1
            }
            for (consumedItem in items) {
                val toBurnFromThis = min(consumedItem.count, countToBurn)
                countToBurn -= toBurnFromThis
                consumedItem.split(toBurnFromThis)
            }
        }
    }

    override fun addToGoggleTooltip(
        portal: ContractPortalBlockEntity,
        tooltip: MutableList<Component>,
        isPlayerSneaking: Boolean
    ): Boolean {
        tooltip.add(Component.translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.header"))

        val portalMode = portal.level?.getBlockState(portal.blockPos)?.getValue(ContractPortalBlock.MODE) ?: return true
        when (portalMode) {
            ContractPortalMode.LIT -> tooltip.add(
                Component
                    .translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.ready")
                    .withStyle(ChatFormatting.GRAY)
            )

            ContractPortalMode.NOT_CONNECTED -> tooltip.add(
                Component
                    .translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.not_connected")
                    .withStyle(ChatFormatting.YELLOW)
            )

            ContractPortalMode.ERROR -> {
                tooltip.add(
                    Component
                        .translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.error_same_player1")
                        .withStyle(ChatFormatting.RED)
                )
                tooltip.add(
                    Component
                        .translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.error_same_player2")
                        .withStyle(ChatFormatting.RED)
                )
                tooltip.add(
                    Component
                        .translatable("${WingsContractsMod.MOD_ID}.gui.goggles.contract_portal.error_same_player3")
                        .withStyle(ChatFormatting.RED)
                )
            }

            else -> CommonComponents.EMPTY
        }

        return true
    }

    override fun save(nbt: CompoundTag): ContractTag {
        val tag = super.save(nbt)
        tag.matchingContractId = matchingContractId
        tag.otherSideCountPerUnit = otherSideCountPerUnit
        tag.otherSideTargets = otherSideTargets
        return tag
    }

    override val details
        get() = BoundContract::class.memberProperties
            .filter { it.name != "details" }
            .associate { prop ->
                return@associate Pair(
                    prop.name, when (prop.name) {
                        "targetItems" -> targetItems.map { it.defaultInstance.details }
                        "targetTags" -> targetTags.map { "#${it.location}" }
                        "targetBlockTags" -> targetBlockTags.map { "#${it.location}" }
                        else -> prop.get(this)
                    })
            }.toMutableMap()

    companion object {
        var (ContractTag).matchingContractId by uuid()
        var (ContractTag).otherSideCountPerUnit by int()
        var (ContractTag).otherSideTargets by csv()

        /** If every item is part of the same currency group, find the smallest item in the list to anchor the price.*/
        private fun deriveCurrencyAnchor(targetItems: List<Item>): Item? {
            if (targetItems.isEmpty()) return null
            val handler = ContractSavedData.fakeData.currencyHandler
            val groups = targetItems.map { handler.itemToCurrencyMap[it] }
            val firstGroup = groups.firstOrNull() ?: return null
            if (groups.any { it !== firstGroup }) return null
            return targetItems.minByOrNull { firstGroup[it] ?: Double.MAX_VALUE }
        }

        fun load(tag: ContractTag, data: ContractSavedData? = null): BoundContract {
            val targetItems = tag.targetItems ?: listOf()
            val explicitAnchor = tag.currencyAnchorItem()
            val derivedAnchor = explicitAnchor ?: deriveCurrencyAnchor(targetItems)
            return BoundContract(
                id = tag.id ?: UUID.randomUUID(),
                targetItems = targetItems,
                targetTags = tag.targetTags ?: listOf(),
                targetBlockTags = tag.targetBlockTags ?: listOf(),
                targetConditions = tag.targetConditions ?: listOf(),
                otherSideCountPerUnit = tag.otherSideCountPerUnit ?: 1,
                otherSideTargets = tag.otherSideTargets ?: listOf(),
                startTime = tag.startTime ?: System.currentTimeMillis(),
                countPerUnit = tag.countPerUnit ?: 64,
                unitsFulfilledEver = tag.unitsFulfilledEver ?: 0,
                author = tag.author ?: "",
                name = tag.name,
                matchingContractId = tag.matchingContractId ?: UUID.randomUUID(),
                isActive = tag.isActive ?: true,
                maxLifetimeUnits = tag.maxLifetimeUnits ?: ModConfig.SERVER.defaultMaxLifetimeUnits.get(),
                currencyAnchor = derivedAnchor,
            )
        }
    }
}
