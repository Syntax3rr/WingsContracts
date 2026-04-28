package dev.biserman.wingscontracts.data

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.config.ModConfig
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.npc.VillagerTrades
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.trading.ItemCost
import net.minecraft.world.item.trading.MerchantOffer
import java.util.Optional
import kotlin.math.ceil
import kotlin.math.min

class CelestialWanderingTradeListing : VillagerTrades.ItemListing {
    override fun getOffer(trader: Entity, random: RandomSource): MerchantOffer? {
        val level = trader.level() as? ServerLevel ?: return null

        val chance = ModConfig.SERVER.celestialWanderingTraderChance.get()
        if (chance <= 0.0 || random.nextDouble() >= chance) return null

        val data = ContractSavedData.get(level)
        if (ContractDataReloadListener.data.availableCelestialRewardPool.isEmpty()) return null

        val contract = data.celestialGenerator.generateContract() ?: return null
        contract.initialize()
        val contractItem = contract.createItem()

        val priceMultiplier = ModConfig.SERVER.celestialWanderingTraderPriceMultiplier.get()
        val priceUnits = ceil(contract.countPerUnit * priceMultiplier).toInt().coerceAtLeast(1)

        val emeraldBlockValue = 9
        val maxBlocks = min(priceUnits / emeraldBlockValue, 64)
        val remainder = priceUnits - maxBlocks * emeraldBlockValue
        if (remainder > 64) {
            WingsContractsMod.LOGGER.debug(
                "Skipping celestial wandering-trader trade: priceUnits=$priceUnits doesn't fit (max=${64 * 9 + 64})"
            )
            return null
        }

        val priceA: ItemCost
        val priceB: Optional<ItemCost>
        when {
            maxBlocks > 0 && remainder > 0 -> {
                priceA = ItemCost(Items.EMERALD_BLOCK, maxBlocks)
                priceB = Optional.of(ItemCost(Items.EMERALD, remainder))
            }
            maxBlocks > 0 -> {
                priceA = ItemCost(Items.EMERALD_BLOCK, maxBlocks)
                priceB = Optional.empty()
            }
            else -> {
                priceA = ItemCost(Items.EMERALD, remainder.coerceAtLeast(1))
                priceB = Optional.empty()
            }
        }

        return MerchantOffer(
            priceA,
            priceB,
            contractItem,
            1,           // maxUses — one trade per spawn
            10,          // xp granted to villager
            0.05f,       // demand-driven price multiplier
        )
    }
}
