package dev.biserman.wingscontracts.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.suggestion.SuggestionProvider
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.command.ModCommand.giveContract
import dev.biserman.wingscontracts.core.CelestialContract
import dev.biserman.wingscontracts.core.Contract.Companion.name
import dev.biserman.wingscontracts.data.ContractDataReloadListener
import dev.biserman.wingscontracts.data.ContractSavedData
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component

object CelestialContractCommand {
    private val celestialContractNames: SuggestionProvider<CommandSourceStack> =
        SuggestionProvider { _, builder ->
            SharedSuggestionProvider.suggest(
                ContractDataReloadListener.data.availableCelestialContracts.mapNotNull { it.name },
                builder
            )
        }

    fun register(): ArgumentBuilder<CommandSourceStack, *> =
        Commands.literal("celestial")
            .then(
                Commands.literal("random")
                    .then(
                        Commands.argument("targets", EntityArgument.players())
                            .executes { context ->
                                giveRandom(context.source, EntityArgument.getPlayer(context, "targets"))
                            })
                    .executes { context -> giveRandom(context.source, context.source.player) }
            )
            .then(
                Commands.literal("defined")
                    .then(
                        Commands.argument("name", StringArgumentType.string())
                            .suggests(celestialContractNames)
                            .then(
                                Commands.argument("targets", EntityArgument.players())
                                    .executes { context ->
                                        giveDefined(
                                            context.source,
                                            StringArgumentType.getString(context, "name"),
                                            EntityArgument.getPlayer(context, "targets"),
                                        )
                                    })
                            .executes { context ->
                                giveDefined(
                                    context.source,
                                    StringArgumentType.getString(context, "name"),
                                    context.source.player,
                                )
                            })
            )

    private fun giveRandom(source: CommandSourceStack, target: net.minecraft.server.level.ServerPlayer?): Int {
        val contract = ContractSavedData.get(source.level).celestialGenerator.generateContract()
        if (contract == null) {
            source.sendFailure(
                Component.translatable("commands.${WingsContractsMod.MOD_ID}.celestial.empty_pool")
            )
            return 0
        }
        return giveContract(source, contract, target)
    }

    private fun giveDefined(
        source: CommandSourceStack,
        name: String,
        target: net.minecraft.server.level.ServerPlayer?,
    ): Int {
        val tag = ContractDataReloadListener.getCelestialContractByName(name)
        if (tag == null) {
            source.sendFailure(
                Component.translatable("commands.${WingsContractsMod.MOD_ID}.celestial.unknown", name)
            )
            return 0
        }
        return giveContract(source, CelestialContract.load(tag, ContractSavedData.get(source.level)), target)
    }
}
