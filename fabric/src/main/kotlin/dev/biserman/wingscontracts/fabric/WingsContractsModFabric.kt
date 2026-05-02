package dev.biserman.wingscontracts.fabric

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.data.CelestialLootInjector
import dev.biserman.wingscontracts.data.CelestialWanderingTradeListing
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.trade.TradeOfferHelper
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.neoforged.fml.config.ModConfig
import dev.biserman.wingscontracts.config.ModConfig as ContractConfig

object WingsContractsModFabric : ModInitializer {
    override fun onInitialize() {
        WingsContractsMod.init()

        NeoForgeConfigRegistry.INSTANCE.register(WingsContractsMod.MOD_ID, ModConfig.Type.SERVER, ContractConfig.SERVER_SPEC)
        NeoForgeConfigRegistry.INSTANCE.register(WingsContractsMod.MOD_ID, ModConfig.Type.COMMON, ContractConfig.COMMON_SPEC)

        TradeOfferHelper.registerWanderingTraderOffers(2) { factories ->
            factories.add(CelestialWanderingTradeListing())
        }

        LootTableEvents.MODIFY.register { key, tableBuilder, _, _ ->
            if (CelestialLootInjector.shouldInject(key.location())) {
                tableBuilder.pool(CelestialLootInjector.buildPool().build())
            }
        }
    }
}
