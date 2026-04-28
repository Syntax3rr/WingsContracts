package dev.biserman.wingscontracts.core

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.data.LoadedContracts
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import dev.biserman.wingscontracts.nbt.ContractTagHelper.boolean
import dev.biserman.wingscontracts.nbt.ContractTagHelper.csv
import dev.biserman.wingscontracts.nbt.ContractTagHelper.int
import dev.biserman.wingscontracts.nbt.ContractTagHelper.itemStack
import dev.biserman.wingscontracts.nbt.ContractTagHelper.long
import dev.biserman.wingscontracts.nbt.ContractTagHelper.string
import dev.biserman.wingscontracts.nbt.ContractTagHelper.uuid
import dev.biserman.wingscontracts.nbt.ItemCondition
import dev.biserman.wingscontracts.nbt.ItemConditionParser
import dev.biserman.wingscontracts.registry.ModItemRegistry
import dev.biserman.wingscontracts.util.ComponentHelper.trimBrackets
import dev.biserman.wingscontracts.util.DenominationsHelper
import net.minecraft.ChatFormatting
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.world.item.*
import net.minecraft.world.level.block.Block
import java.util.*
import kotlin.math.min

fun (Item).name(): String = this.defaultInstance.displayName.string
fun <T> (TagKey<T>).name(): String {
    val path = this.location.path
    val firstLetterCapital = path.substring(0, 1).uppercase(Locale.getDefault())
    return "${firstLetterCapital}${path.substring(1)}"
}

@Suppress("MemberVisibilityCanBePrivate")
abstract class Contract(
    val id: UUID = UUID.randomUUID(),
    val targetItems: List<Item> = listOf(),
    val targetTags: List<TagKey<Item>> = listOf(),
    val targetBlockTags: List<TagKey<Block>> = listOf(),
    val targetConditions: List<ItemCondition> = listOf(),

    var startTime: Long = System.currentTimeMillis(),

    val countPerUnit: Int = 1,
    var unitsFulfilledEver: Long = 0,

    val author: String = "",
    val name: String? = null,
    val description: String? = null,
    val shortTargetList: String? = null,
    val displayItem: ItemStack? = null,
    val rarity: Int? = null,

    var isActive: Boolean = true,
    val maxLifetimeUnits: Int = 0,

    val currencyAnchor: Item? = null,
) {
    abstract val type: ContractType

    fun matches(itemStack: ItemStack): Boolean {
        // fail to match everything when disabled
        if (isDisabled) {
            return false
        }

        // if any targetCondition fails, return false
        if (targetConditions.isNotEmpty()) {
            if (targetConditions.any { !it.match(itemStack) }) {
                return false
            }
        }

        if (currencyAnchor != null) {
            return denominationMap()?.containsKey(itemStack.item) == true
        }

        if (targetTags.isNotEmpty()) {
            return targetTags.any { itemStack.`is`(it) }
        }

        val blockItem = itemStack.item as? BlockItem
        if (blockItem != null && targetBlockTags.isNotEmpty()) {
            return targetBlockTags.any { blockItem.block.defaultBlockState().`is`(it) }
        }

        if (targetItems.isNotEmpty()) {
            return targetItems.any { itemStack.item == it }
        }

        return targetConditions.isNotEmpty() // blank contracts return false unless they have nbt conditions
    }

    private fun denominationMap(): Map<Item, Double>? {
        val anchor = currencyAnchor ?: return null
        return ContractSavedData.fakeData.currencyHandler.itemToCurrencyMap[anchor]
    }

    open val isDisabled get() = !isActive

    internal fun recordFulfilment(units: Int, tag: ContractTag) {
        unitsFulfilledEver += units
        tag.unitsFulfilledEver = unitsFulfilledEver
        if (maxLifetimeUnits > 0 && unitsFulfilledEver >= maxLifetimeUnits) {
            isActive = false
            tag.isActive = isActive
        }
    }

    val displayItems by lazy {
        if (displayItem == null) {
            if (targetItems.isEmpty() && targetTags.isEmpty() && targetBlockTags.isEmpty()) {
                listOf(ModItemRegistry.QUESTION_MARK.get()?.defaultInstance ?: ItemStack.EMPTY)
            } else {
                targetItems.map { it.defaultInstance }
                    .plus(targetTags.flatMap {
                        BuiltInRegistries.ITEM.getTagOrEmpty(it)
                            .map { holder -> holder.value().defaultInstance }
                    }).plus(targetBlockTags.flatMap {
                        BuiltInRegistries.BLOCK.getTagOrEmpty(it)
                            .map { holder -> holder.value().asItem().defaultInstance }
                    })
            }
        } else {
            listOf(displayItem)
        }
    }

    open val targetName: String by lazy {
        val targetComponentCount = targetItems.size + targetTags.size + targetBlockTags.size
        if (targetComponentCount > 1) {
            return@lazy translateContract("complex").string
        }

        if (targetItems.isNotEmpty()) {
            return@lazy if (displayItem != null) {
                displayItem.displayName.string.trimBrackets()
            } else {
                targetItems[0].name().trimBrackets()
            }
        }

        if (targetTags.isNotEmpty()) {
            return@lazy targetTags[0].name()
                .split("/")
                .reversed()
                .flatMap { it.split("_") }
                .joinToString(" ") { it.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
        }

        if (targetBlockTags.isNotEmpty()) {
            return@lazy targetBlockTags[0].name()
                .split("/")
                .reversed()
                .flatMap { it.split("_") }
                .joinToString(" ") { it.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
        }

        return@lazy translateContract("empty").string
    }

    open fun tryUpdateTick(tag: ContractTag): Boolean = false

    open fun renew(tag: ContractTag, cyclesPassed: Int, newCycleStart: Long) {}

    open fun onContractFulfilled(tag: ContractTag) {}

    open fun getDisplayName(rarity: Int): MutableComponent = Component.translatable(
        "item.${WingsContractsMod.MOD_ID}.contract", Component.translatable(name ?: targetName).string
    )

    fun getTargetListComponents(displayShort: Boolean): List<MutableComponent> {
        val totalSize = targetItems.size + targetTags.size + targetBlockTags.size

        val tagKey = if (displayShort) "items_of_tag_short" else "items_of_tag"
        val complexPrefix = if (displayShort) "" else " - "

        if (displayShort) {
            if (shortTargetList != null) {
                return listOf(Component.translatable(shortTargetList))
            } else if (totalSize > 3) {
                return listOf(Component.translatable(name ?: targetName))
            }
        }

        return when (totalSize) {
            0 -> listOf(translateContract("no_targets"))
            1 -> if (targetItems.isNotEmpty()) {
                listOf(
                    Component.literal(
                        if (displayItem != null) {
                            displayItem.displayName.string.trimBrackets()
                        } else {
                            targetItems[0].name().trimBrackets()
                        }
                    )
                )
            } else if (targetTags.isNotEmpty()) {
                listOf(translateContract(tagKey, targetTags[0].location()))
            } else if (targetBlockTags.isNotEmpty()) {
                listOf(translateContract(tagKey, targetBlockTags[0].location()))
            } else {
                listOf(Component.literal(""))
            }

            else -> {
                val start = if (displayShort) listOf() else listOf(translateContract("matches_following"))
                start + listTargets.map { Component.literal("$complexPrefix$it") }
            }
        }
    }

    val listTargets: List<String>
        get() = targetItems
            .asSequence()
            .map { it.name().trimBrackets() }
            .plus(targetTags.map { it.name() })
            .plus(targetBlockTags.map { it.name() })
            .toList()

    open fun getBasicInfo(list: MutableList<Component>?): MutableList<Component> = list ?: mutableListOf()

    open fun getShortInfo(): Component = Component.empty()

    open fun getExtraInfo(
        list: MutableList<Component>?
    ): MutableList<Component> {
        val components = mutableListOf<Component>()

        components.addAll(getBasicInfo(null))


        if (this is ServerContract) {
            components.addAll(getCycleInfo())
        }

        components.add(
            translateContract(
                "total_fulfilled",
                unitsFulfilledEver,
                unitsFulfilledEver * countPerUnit,
                unitsFulfilledEver * rewardPerUnit
            ).withStyle(ChatFormatting.LIGHT_PURPLE)
        )

        if (author.isNotBlank()) {
            components.add(
                translateContract(
                    "author",
                    Component.translatable(author).string
                ).withStyle(ChatFormatting.DARK_PURPLE)
            )
        }

        return components
    }

    open fun getDescription(
        showExtraInfo: Boolean, howExtraInfo: Component
    ): MutableList<Component> {
        val components = mutableListOf<Component>()

        if (showExtraInfo) {
            components.addAll(getExtraInfo(null))
        } else {
            components.add(getShortInfo())
            if (!description.isNullOrBlank()) {
                components.add(Component.translatable(description).withStyle(ChatFormatting.GRAY))
            }
            components.add(howExtraInfo)
        }

        return components
    }

    open fun countConsumableUnits(items: NonNullList<ItemStack>): Int {
        val denominations = denominationMap()
        if (denominations != null) {
            val unitValue = unitValueInDenominations(denominations)
            if (unitValue <= 0) return 0
            val totalValue = items
                .filter { !it.isEmpty && denominations.containsKey(it.item) }
                .sumOf { (denominations[it.item]!!.toLong()) * it.count }
            return (totalValue / unitValue).toInt()
        }

        val matchingStacks = items.filter { !it.isEmpty && matches(it) }
        val matchingCount = matchingStacks.sumOf { it.count }
        return matchingCount / countPerUnit
    }

    private fun unitValueInDenominations(denominations: Map<Item, Double>): Long {
        val anchorValue = denominations[currencyAnchor ?: return 0]?.toLong() ?: return 0
        return countPerUnit * anchorValue
    }

    abstract fun tryConsumeFromItems(tag: ContractTag, portal: ContractPortalBlockEntity): ConsumeResult

    open fun consumeUnits(unitCount: Int, portal: ContractPortalBlockEntity): List<ItemStack> {
        val denominations = denominationMap()
        if (denominations != null) {
            return consumeCurrencyUnits(unitCount, portal, denominations)
        }

        val goalAmount = unitCount * countPerUnit
        var amountTaken = 0
        val consumedItems = mutableListOf<ItemStack>()
        for (itemStack in portal.cachedInput.items) {
            if (amountTaken >= goalAmount) {
                break
            }

            if (itemStack.isEmpty) {
                continue
            }

            if (matches(itemStack)) {
                val amountToTake = min(itemStack.count, goalAmount - amountTaken)
                amountTaken += amountToTake
                consumedItems.add(itemStack.split(amountToTake))
            }
        }

        return consumedItems
    }

    private fun consumeCurrencyUnits(
        unitCount: Int,
        portal: ContractPortalBlockEntity,
        denominations: Map<Item, Double>,
    ): List<ItemStack> {
        var remaining = unitCount * unitValueInDenominations(denominations)
        if (remaining <= 0) return emptyList()
        val out = mutableListOf<ItemStack>()

        val sortedSlots = portal.cachedInput.items
            .filter { !it.isEmpty && denominations.containsKey(it.item) }
            .sortedBy { denominations[it.item]!! }
        for (stack in sortedSlots) {
            if (remaining <= 0) break
            val itemValue = denominations[stack.item]!!.toLong()
            if (itemValue <= 0) continue
            val maxTake = (remaining / itemValue).toInt()
            val take = min(stack.count, maxTake)
            if (take > 0) {
                out.add(stack.split(take))
                remaining -= take * itemValue
            }
        }

        if (remaining > 0) { // Try to break a larger stack to cover remaining
            val breakable = portal.cachedInput.items
                .filter {
                    !it.isEmpty
                            && denominations.containsKey(it.item)
                            && denominations[it.item]!!.toLong() >= remaining
                }
                .minByOrNull { denominations[it.item]!! }
            if (breakable != null) {
                val itemValue = denominations[breakable.item]!!.toLong()
                out.add(breakable.split(1))
                val change = itemValue - remaining
                if (change > 0) {
                    for ((item, count) in DenominationsHelper.denominate(change.toDouble(), denominations)) {
                        var leftover = count
                        while (leftover > 0) {
                            val take = min(leftover, item.defaultMaxStackSize)
                            portal.cachedInput.addItem(ItemStack(item, take))
                            leftover -= take
                        }
                    }
                    portal.normalizeCurrencyInput(this)
                }
            }
        }

        return out
    }

    open fun addToGoggleTooltip(
        portal: ContractPortalBlockEntity,
        tooltip: MutableList<Component>,
        isPlayerSneaking: Boolean
    ) = false

    abstract val rewardPerUnit: Int

    open fun save(nbt: CompoundTag): ContractTag {
        val tag = ContractTag(nbt)

        tag.type = type
        tag.id = id
        tag.targetItems = targetItems
        tag.targetTags = targetTags
        tag.targetBlockTags = targetBlockTags
        tag.targetConditions = targetConditions
        tag.startTime = startTime
        tag.countPerUnit = countPerUnit
        tag.unitsFulfilledEver = unitsFulfilledEver
        tag.rarity = rarity
        tag.author = author
        tag.name = name
        tag.description = description
        tag.shortTargetList = shortTargetList
        tag.displayItem = displayItem
        tag.isActive = isActive
        tag.maxLifetimeUnits = maxLifetimeUnits
        tag.currencyAnchor = currencyAnchor?.let { BuiltInRegistries.ITEM.getKey(it)?.toString() }

        return tag
    }

    open val isComplete get() = false

    abstract val item: Item
    fun createItem(): ItemStack {
        val itemStack = ItemStack(item)

        val tag = save(CompoundTag())
        ContractTagHelper.setContractTag(itemStack, tag)
        LoadedContracts.update(this)
        if (rarity != null) {
            itemStack.set(DataComponents.RARITY, Rarity.BY_ID.apply(rarity))
        }

        return itemStack
    }

    abstract val details: MutableMap<String, Any?>

    companion object {
        var (ContractTag).type: ContractType?
            get() = if (tag.contains("type")) ContractType.fromId(tag.getInt("type")) else null
            set(value) {
                if (value != null) tag.putInt("type", value.id)
            }
        var (ContractTag).id by uuid()

        var (ContractTag).isActive by boolean()
        var (ContractTag).maxLifetimeUnits by int()
        var (ContractTag).currencyAnchor by string()

        fun (ContractTag).currencyAnchorItem(): Item? {
            val key = currencyAnchor ?: return null
            val resolved = BuiltInRegistries.ITEM[ResourceLocation.tryParse(key) ?: return null]
            return if (resolved == Items.AIR) null else resolved
        }

        var (ContractTag).targetItemKeys by csv("targetItems")
        var (ContractTag).targetTagKeys by csv("targetTags")
        var (ContractTag).targetBlockTagKeys by csv("targetBlockTags")
        var (ContractTag).targetConditionsKeys by string("targetConditions")

        var (ContractTag).startTime by long()

        var (ContractTag).countPerUnit by int()
        var (ContractTag).unitsFulfilledEver by long()
        var (ContractTag).rarity by int()

        var (ContractTag).author by string()
        var (ContractTag).name by string()
        var (ContractTag).description by string()
        var (ContractTag).shortTargetList by string()
        var (ContractTag).displayItem by itemStack()

        val (ContractTag).requiresAll by string() // only needed at gen-time
        val (ContractTag).requiresAny by string() // only needed at gen-time
        val (ContractTag).requiresNot by string() // only needed at gen-time

        var (ContractTag).targetItems: List<Item>?
            get() {
                val targetItems = targetItemKeys ?: return null
                if (targetItems.isNotEmpty()) {
                    return targetItems.map {
                        BuiltInRegistries.ITEM[ResourceLocation.tryParse(it)]
                    }.filter { it != Items.AIR }
                }

                return null
            }
            set(value) {
                targetItemKeys = value?.mapNotNull { BuiltInRegistries.ITEM.getKey(it)?.toString() }
            }


        var (ContractTag).targetTags: List<TagKey<Item>>?
            get() {
                val tagKeys = targetTagKeys ?: return null

                if (tagKeys.isNotEmpty()) {
                    return tagKeys.map { it.trimStart('#') }.mapNotNull {
                        return@mapNotNull TagKey.create(
                            Registries.ITEM, ResourceLocation.tryParse(it) ?: return@mapNotNull null
                        )
                    }
                }

                return null
            }
            set(value) {
                targetTagKeys = value?.map { it.location().toString() }
            }

        var (ContractTag).targetBlockTags: List<TagKey<Block>>?
            get() {
                val blockTagKeys = targetBlockTagKeys ?: return null

                if (blockTagKeys.isNotEmpty()) {
                    return blockTagKeys.map { it.trimStart('#') }.mapNotNull {
                        return@mapNotNull TagKey.create(
                            Registries.BLOCK, ResourceLocation.tryParse(it) ?: return@mapNotNull null
                        )
                    }
                }

                return null
            }
            set(value) {
                targetBlockTagKeys = value?.map { it.location().toString() }
            }

        var (ContractTag).targetConditions: List<ItemCondition>?
            get() {
                if (targetConditionsKeys.isNullOrBlank()) {
                    return null
                }

                return ItemConditionParser.parse(targetConditionsKeys!!)
            }
            set(value) {
                targetConditionsKeys = value?.mapNotNull { it.text }?.joinToString(",")
            }

        fun translateContract(key: String, vararg objects: Any): MutableComponent =
            Component.translatable(
                "${WingsContractsMod.MOD_ID}.contract.$key",
                *(objects.map { if (it is ResourceLocation) it.toString() else it }.toTypedArray())
            )

        fun getDisplayItem(itemStack: ItemStack, time: Float): ItemStack {
            val contract = LoadedContracts[itemStack] ?: return ItemStack.EMPTY

            return if (contract.displayItems.isEmpty()) {
                ItemStack.EMPTY
            } else {
                contract.displayItems[Mth.floor(time / 30.0f) % contract.displayItems.size]
            }
        }

        fun getTimeRemainingColor(timeRemaining: Long) = when {
            timeRemaining < 1000 * 60 * 60 -> ChatFormatting.RED
            timeRemaining < 1000 * 60 * 60 * 24 -> ChatFormatting.YELLOW
            else -> ChatFormatting.DARK_PURPLE
        }
    }
}
