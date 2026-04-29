package dev.biserman.wingscontracts.config

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.util.DenominationsHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth.ceil
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.min

class DenominatedCurrenciesHandler() {
    private val denominatedCurrencies by lazy {
        ModConfig.SERVER.denominations.get().split(";").map {
            it.split(",").filter { entry -> "=" in entry }.mapNotNull entry@{ entry ->
                val (itemId, valueString) = entry.split("=")
                val resourceLocation = ResourceLocation.tryParse(itemId.trim())

                if (resourceLocation == null) {
                    WingsContractsMod.LOGGER.warn("Failed to parse currency denomination ${itemId.trim()}. Skipping...")
                    return@entry null
                }

                val value = valueString.trim().toInt()
                val item = BuiltInRegistries.ITEM.get(resourceLocation)
                if (item == Items.AIR) {
                    WingsContractsMod.LOGGER.warn("Could not find item with ID ${itemId.trim()}. Skipping...")
                    return@entry null
                }

                if (value <= 0) {
                    WingsContractsMod.LOGGER.warn("Invalid denomination value for ${itemId.trim()}: denomination values must be positive. Skipping...")
                    return@entry null
                }

                return@entry Pair(item, value)
            }
        }
    }

    private val currencyMaps by lazy {
        denominatedCurrencies.map { pairList ->
            pairList.associate {
                Pair(
                    it.first, it.second.toDouble()
                )
            }
        }
    }

    val itemToCurrencyMap by lazy {
        val currencies = currencyMaps
            .flatMap { currencyMap ->
                currencyMap.entries
                    .map { Pair(it.key, currencyMap) }
            }

        if (currencies.distinctBy { it.first }.size != currencies.size) {
            throw Error("An item appears twice in the denominations value of ${WingsContractsMod.MOD_ID}-server.toml")
        }

        currencies.toMap()
    }

    fun isCurrency(itemStack: ItemStack) = itemStack.item in itemToCurrencyMap

    fun splitHighestDenomination(itemStack: ItemStack): ItemStack {
        val denominations =
            itemToCurrencyMap[itemStack.item] ?: return itemStack.split(min(itemStack.count, itemStack.maxStackSize))

        val itemValue = denominations[itemStack.item] ?: throw Error()
        val stackValue = itemValue * itemStack.count

        val (splitItem, splitCount) = DenominationsHelper.getLargestDenomination(stackValue, denominations)
            ?: return itemStack.split(min(itemStack.count, itemStack.maxStackSize))

        val cappedSplitCount = min(splitCount, splitItem.defaultMaxStackSize)
        val splitItemValue = denominations[splitItem] ?: throw Error()
        val splitStackValue = cappedSplitCount * splitItemValue

        itemStack.shrink(ceil(splitStackValue / itemValue))
        return ItemStack(splitItem, cappedSplitCount)
    }

    fun denominateCurrency(itemStack: ItemStack) = iterator {
        val denominations = itemToCurrencyMap[itemStack.item]
        if (denominations == null) {
            yield(itemStack)
            return@iterator
        }

        val itemValue = denominations[itemStack.item] ?: throw Error()
        val stackValue = itemValue * itemStack.count

        yieldAll(
            DenominationsHelper.denominate(stackValue, denominations).asSequence()
                .map { ItemStack(it.first, it.second) })
    }
}