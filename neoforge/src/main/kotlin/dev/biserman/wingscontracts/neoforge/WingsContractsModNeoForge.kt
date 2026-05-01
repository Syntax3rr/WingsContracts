package dev.biserman.wingscontracts.neoforge

import com.mojang.serialization.MapCodec
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.data.CelestialWanderingTradeListing
import dev.biserman.wingscontracts.gui.AvailableContractsScreen
import dev.biserman.wingscontracts.gui.BoundContractCreationScreen
import dev.biserman.wingscontracts.neoforge.compat.ForgeModCompat
import dev.biserman.wingscontracts.neoforge.data.CelestialLootModifier
import dev.biserman.wingscontracts.registry.ModMenuRegistry
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.loot.IGlobalLootModifier
import net.neoforged.neoforge.event.village.WandererTradesEvent
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import net.neoforged.fml.config.ModConfig as NeoForgeConfig

@Mod(WingsContractsMod.MOD_ID)
class WingsContractsModNeoForge(bus: IEventBus, container: ModContainer) {
    init {
        container.registerConfig(NeoForgeConfig.Type.SERVER, ModConfig.SERVER_SPEC)

        val glmSerializers: DeferredRegister<MapCodec<out IGlobalLootModifier>> =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, WingsContractsMod.MOD_ID)
        glmSerializers.register("celestial_loot", java.util.function.Supplier { CelestialLootModifier.CODEC })
        glmSerializers.register(bus)

        bus.register(object {
            @SubscribeEvent
            fun onRegisterMenuScreens(event: RegisterMenuScreensEvent) {
                event.register(ModMenuRegistry.CONTRACT_PORTAL.get(), ::AvailableContractsScreen)
                event.register(ModMenuRegistry.BOUND_CONTRACT_CREATION.get(), ::BoundContractCreationScreen)
            }
        })

        NeoForge.EVENT_BUS.addListener<WandererTradesEvent> { event ->
            event.rareTrades.add(CelestialWanderingTradeListing())
        }

        CelestialRerollEvents.register(NeoForge.EVENT_BUS)

        WingsContractsMod.init()
        ForgeModCompat.init(bus)
    }
}
