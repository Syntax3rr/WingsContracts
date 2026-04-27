package dev.biserman.wingscontracts.data

import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.core.AbyssalContract
import dev.biserman.wingscontracts.core.Contract.Companion.countPerUnit
import dev.biserman.wingscontracts.core.ServerContract.Companion.baseUnitsDemanded
import dev.biserman.wingscontracts.core.ServerContract.Companion.currentCycleStart
import dev.biserman.wingscontracts.core.ServerContract.Companion.reward
import dev.biserman.wingscontracts.core.Contract.Companion.rarity
import dev.biserman.wingscontracts.core.Contract.Companion.startTime
import dev.biserman.wingscontracts.core.Contract.Companion.targetConditions
import dev.biserman.wingscontracts.core.Contract.Companion.targetItems
import dev.biserman.wingscontracts.data.ContractSavedData.Companion.random
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.Reward
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

class AbyssalContractGenerator(val data: ContractSavedData) {
    private fun vary(value: Double, multiplier: Double): Double {
        val variance = ModConfig.SERVER.variance.get()
        val randomFactor = random.nextDouble()
        val varianceFactor = 1.0 + (randomFactor * 2.0 - 1.0) * variance

        return max(1.0, value * multiplier * varianceFactor)
    }

    fun generateContract(tag: ContractTag): AbyssalContract {
        tag.currentCycleStart = data.currentCycleStart
        tag.startTime = data.currentCycleStart
        tag.baseUnitsDemanded =
            max(
                vary(
                    tag.baseUnitsDemanded?.toDouble() ?: 64.0,
                    ModConfig.SERVER.defaultUnitsDemandedMultiplier.get()
                ).roundToInt(), 1
            )
        tag.countPerUnit =
            max(
                vary(
                    tag.countPerUnit?.toDouble() ?: 16.0,
                    ModConfig.SERVER.defaultCountPerUnitMultiplier.get()
                ).roundToInt(), 1
            )
        val reward = tag.reward ?: Reward.Random(1.0)
        if (reward is Reward.Random) {
            val rewardValue = vary(reward.value, ModConfig.SERVER.defaultRewardMultiplier.get())
            if (random.nextDouble() <= ModConfig.SERVER.replaceRewardWithRandomRate.get()) {
                for (_try in 1..5) { // attempt 5 times to find a working item
                    val otherContract = ContractDataReloadListener.randomTag()
                    val otherContractItem = otherContract.targetItems?.get(0) ?: continue

                    // skip multi-item contracts
                    if (otherContract.targetItems?.size != 1) {
                        continue
                    }

                    // skip contracts with conditions
                    if ((otherContract.targetConditions?.size ?: 0) != 0) {
                        continue
                    }

                    // skip contracts that are on the reward blocklist
                    if (ContractDataReloadListener.data.rewardBlocklist
                            .contains(BuiltInRegistries.ITEM.getKey(otherContractItem)?.toString() ?: "")
                    ) {
                        continue
                    }

                    // skip contracts that reward a valid currency (this helps mitigate exchange rate issues)
                    if (data.currencyHandler.isCurrency(otherContractItem.defaultInstance)) {
                        continue
                    }

                    // skip contracts that reward nothing (sanity check)
                    if (otherContractItem.asItem() == Items.AIR) {
                        continue
                    }

                    // skip contracts that would reward the input item
                    if (tag.targetItems?.size == 1 && otherContractItem.asItem() == tag.targetItems?.get(0)) {
                        continue
                    }

                    // skip contracts that use a non-standard reward
                    val otherContractReward = otherContract.reward
                    if (otherContractReward !is Reward.Random) {
                        continue
                    }

                    val otherContractCountPerUnit = otherContract.countPerUnit?.toDouble() ?: 16.0
                    val otherContractRewardValue = (otherContractReward.value
                            * ModConfig.SERVER.defaultRewardMultiplier.get())
                    val newRewardCount = (rewardValue.pow(2)
                            * otherContractCountPerUnit
                            * ModConfig.SERVER.replaceRewardWithRandomFactor.get()
                            / otherContractRewardValue.pow(2)
                            ).roundToInt()

                    // skip contracts with too relatively cheap a reward
                    if (newRewardCount <= 0 || newRewardCount >= 128) {
                        continue
                    }

                    tag.reward = Reward.Defined(ItemStack(otherContractItem, newRewardCount))
                    break
                }
            }

            if (tag.reward is Reward.Random) {
                tag.reward = Reward.Defined(getRandomReward(rewardValue))
            }

            tag.rarity = tag.rarity ?: AbyssalContract.load(tag, data).calculateRarity(data, reward.value)
        }

        return AbyssalContract.load(tag, data)
    }

    private fun getCount(reward: RewardBagEntry, value: Double) = round(value / reward.value).toInt()
    fun getRandomReward(value: Double): ItemStack {
        val sufficientlyCheapRewardsBag = ContractDataReloadListener.data.defaultRewards.filter { getCount(it, value) >= 1 }
        val sufficientlyCheapRewardsWeightSum = sufficientlyCheapRewardsBag.sumOf { it.weight }

        if (sufficientlyCheapRewardsWeightSum > 0) {
            val pick = random.nextInt(sufficientlyCheapRewardsWeightSum)
            var runningWeight = 0
            for (reward in sufficientlyCheapRewardsBag) {
                runningWeight += reward.weight
                if (pick < runningWeight) {
                    return reward.item.copyWithCount(reward.item.count * getCount(reward, value))
                }
            }
        }

        val lastFit = ContractDataReloadListener.data.defaultRewards.lastOrNull { getCount(it, value) >= 1 }
        if (lastFit != null) {
            return lastFit.item.copyWithCount(lastFit.item.count * getCount(lastFit, value))
        }
        val cheapest = ContractDataReloadListener.data.defaultRewards.minByOrNull { it.value }
        return cheapest?.item?.copy() ?: ContractSavedData.FALLBACK_REWARD.item.copy()
    }
}