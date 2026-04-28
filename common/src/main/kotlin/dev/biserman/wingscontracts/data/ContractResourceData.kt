package dev.biserman.wingscontracts.data

import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.nbt.ContractTag
import net.minecraft.world.item.ItemStack

data class ContractResourceData(
    val allAvailableContracts: List<ContractTag>,
    val nonDefaultAvailableContracts: List<ContractTag>,
    val allDefaultRewards: List<RewardBagEntry>,
    val nonDefaultDefaultRewards: List<RewardBagEntry>, // funny name, but it refers to custom-specified default rewards,
    val fullRewardBlocklist: List<String>,
    val nonDefaultRewardBlocklist: List<String>,
    val allAvailableCelestialContracts: List<ContractTag>,
    val nonDefaultAvailableCelestialContracts: List<ContractTag>,
    val allAvailableCelestialRewardPool: List<CelestialRewardPoolEntry>,
    val nonDefaultAvailableCelestialRewardPool: List<CelestialRewardPoolEntry>,
    val version: Int,
) {
    constructor() : this(
        allAvailableContracts = listOf(),
        nonDefaultAvailableContracts = listOf(),
        allDefaultRewards = listOf(),
        nonDefaultDefaultRewards = listOf(),
        fullRewardBlocklist = listOf(),
        nonDefaultRewardBlocklist = listOf(),
        allAvailableCelestialContracts = listOf(),
        nonDefaultAvailableCelestialContracts = listOf(),
        allAvailableCelestialRewardPool = listOf(),
        nonDefaultAvailableCelestialRewardPool = listOf(),
        version = 0,
    )

    val availableContracts
        get() = if (ModConfig.SERVER.disableDefaultContractOptions.get()) {
            nonDefaultAvailableContracts.toList()
        } else {
            allAvailableContracts.toList()
        }

    val defaultRewards
        get() = if (ModConfig.SERVER.disableDefaultContractOptions.get()) {
            nonDefaultDefaultRewards.toList()
        } else {
            allDefaultRewards.toList()
        }

    fun valueReward(itemStack: ItemStack): Double {
        val reward = defaultRewards.find { it.item.item == itemStack.item } ?: return 0.0
        return itemStack.count * reward.value / reward.item.count
    }

    val rewardBlocklist
        get() = if (ModConfig.SERVER.disableDefaultContractOptions.get()) {
            nonDefaultRewardBlocklist.toList()
        } else {
            fullRewardBlocklist.toList()
        }

    val availableCelestialContracts
        get() = if (ModConfig.SERVER.disableDefaultCelestialContractOptions.get()) {
            nonDefaultAvailableCelestialContracts.toList()
        } else {
            allAvailableCelestialContracts.toList()
        }

    val availableCelestialRewardPool
        get() = if (ModConfig.SERVER.disableDefaultCelestialContractOptions.get()) {
            nonDefaultAvailableCelestialRewardPool.toList()
        } else {
            allAvailableCelestialRewardPool.toList()
        }
}
