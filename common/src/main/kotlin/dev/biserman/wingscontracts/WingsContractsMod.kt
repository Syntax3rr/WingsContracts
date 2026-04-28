package dev.biserman.wingscontracts

import dev.architectury.event.events.client.ClientLifecycleEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.TickEvent
import dev.architectury.registry.client.level.entity.EntityRendererRegistry
import dev.architectury.utils.Env
import dev.architectury.utils.EnvExecutor
import dev.biserman.wingscontracts.client.WingsContractsClient
import dev.biserman.wingscontracts.client.renderer.FakeItemEntityRenderer
import dev.biserman.wingscontracts.compat.CompatMods
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.data.LoadedContracts
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import dev.biserman.wingscontracts.registry.*
import dev.biserman.wingscontracts.scoreboard.ScoreboardHandler
import dev.biserman.wingscontracts.server.WingsContractsNetHandler
import net.minecraft.resources.ResourceLocation
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object WingsContractsMod {
    const val MOD_ID: String = "wingscontracts"
    val LOGGER: Logger = LogManager.getLogger("WingsContracts")

    fun init() {
        ModBlockRegistry.register()
        ModItemRegistry.register()
        ModBlockEntityRegistry.register()
        ModEntityRegistry.register()
        ModSoundRegistry.register()
        ModLootEntryRegistry.register()
        ModCommandRegistry.register()
        ModMenuRegistry.register()
        ModReloadListenerRegistry.register()
        ModCriteriaTriggerRegistry.register()

        WingsContractsNetHandler.init()

        CompatMods.init()

        LifecycleEvent.SERVER_LEVEL_LOAD.register(ScoreboardHandler::init)
        LifecycleEvent.SERVER_LEVEL_LOAD.register { level -> ContractTagHelper.registryAccess = level.registryAccess() }

        TickEvent.Server.SERVER_LEVEL_POST.register { level ->
            ContractTagHelper.registryAccess = level.registryAccess()
            ContractSavedData.get(level).serverTick(level)
        }

        EnvExecutor.runInEnv(Env.CLIENT) {
            Runnable {
                ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register { level ->
                    ContractTagHelper.registryAccess = level.registryAccess()
                }
                ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register { level ->
                    LoadedContracts.clear()
                    ContractSavedData.fakeData =
                        ContractSavedData()
                }

                ClientLifecycleEvent.CLIENT_SETUP.register(ClientLifecycleEvent.ClientState {
                    WingsContractsClient.init()
                    ModMenuRegistry.clientsideRegister()
                })

                EntityRendererRegistry.register(
                    ModEntityRegistry.FAKE_ITEM
                ) { FakeItemEntityRenderer(it) }
            }
        }
    }

    fun prefix(path: String): ResourceLocation = ResourceLocation.parse("$MOD_ID:$path")!!
}
