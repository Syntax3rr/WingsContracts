package dev.biserman.wingscontracts.neoforge

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.gui.AvailableContractsScreen
import dev.biserman.wingscontracts.gui.BoundContractCreationScreen
import dev.biserman.wingscontracts.neoforge.compat.ForgeModCompat
import dev.biserman.wingscontracts.registry.ModMenuRegistry
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.fml.config.ModConfig as NeoForgeConfig

@Mod(WingsContractsMod.MOD_ID)
class WingsContractsModNeoForge(bus: IEventBus, container: ModContainer) {
    init {
        container.registerConfig(NeoForgeConfig.Type.SERVER, ModConfig.SERVER_SPEC)

        bus.register(object {
            @SubscribeEvent
            fun onRegisterMenuScreens(event: RegisterMenuScreensEvent) {
                event.register(ModMenuRegistry.CONTRACT_PORTAL.get(), ::AvailableContractsScreen)
                event.register(ModMenuRegistry.BOUND_CONTRACT_CREATION.get(), ::BoundContractCreationScreen)
            }
        })

        WingsContractsMod.init()
        ForgeModCompat.init(bus)
    }
}
