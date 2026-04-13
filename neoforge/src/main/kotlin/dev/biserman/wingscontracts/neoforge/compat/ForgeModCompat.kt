package dev.biserman.wingscontracts.neoforge.compat

import com.simibubi.create.api.behaviour.display.DisplaySource
import dev.architectury.event.events.client.ClientLifecycleEvent
import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import dev.architectury.utils.EnvExecutor
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.client.ponder.ModPonderPlugin
import dev.biserman.wingscontracts.compat.CompatMods
import dev.biserman.wingscontracts.compat.create.LeaderboardDisplaySource
import dev.biserman.wingscontracts.scoreboard.ScoreboardHandler
import net.createmod.ponder.foundation.PonderIndex
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.neoforged.bus.api.IEventBus

object ForgeModCompat {
    @Suppress("DEPRECATION")
    fun init(modBus: IEventBus) {
        if (Platform.isModLoaded(CompatMods.CREATE)) {
            val createRegistrate = KotlinCreateRegistrate(WingsContractsMod.MOD_ID)
            createRegistrate.registerEventListeners(modBus)

            fun registerLeaderboard(name: String, factory: () -> LeaderboardDisplaySource) {
                createRegistrate
                    .displaySource(name, factory)
                    .onRegisterAfter(Registries.BLOCK_ENTITY_TYPE) { source ->
                        val id = WingsContractsMod.prefix("contract_portal_be")
                        if (BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
                            DisplaySource.BY_BLOCK_ENTITY.add(
                                BuiltInRegistries.BLOCK_ENTITY_TYPE.get(id),
                                source
                            )
                        } else {
                            WingsContractsMod.LOGGER.warn("Could not find block entity type $id. Outdated compat?")
                        }
                    }
                    .register()
            }

            registerLeaderboard("leaderboard") {
                LeaderboardDisplaySource(
                    ScoreboardHandler.CONTRACT_SCORE,
                    "main"
                )
            }

            registerLeaderboard("leaderboard_periodic") {
                LeaderboardDisplaySource(
                    ScoreboardHandler.CONTRACT_SCORE_PERIODIC,
                    "periodic"
                )
            }

            EnvExecutor.runInEnv(Env.CLIENT) {
                Runnable {
                    ClientLifecycleEvent.CLIENT_SETUP.register(ClientLifecycleEvent.ClientState {
                        if (Platform.isModLoaded("create")) {
                            PonderIndex.addPlugin(ModPonderPlugin)
                        }
                    })
                }
            }
        }

        if (Platform.isModLoaded(CompatMods.COMPUTERCRAFT)) {
            modBus.addListener(ModPeripheralProvider::registerCapability)
        }
    }
}