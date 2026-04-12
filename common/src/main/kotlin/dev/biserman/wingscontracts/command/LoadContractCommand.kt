package dev.biserman.wingscontracts.command

import com.google.gson.JsonParser
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.command.ModCommand.giveContract
import dev.biserman.wingscontracts.core.AbyssalContract
import dev.biserman.wingscontracts.core.BoundContract
import dev.biserman.wingscontracts.core.Contract
import dev.biserman.wingscontracts.data.ContractDataReloadListener
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.nbt.ContractTag
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.world.level.Level

object LoadContractCommand {
    val options = mapOf<String, (Level, ContractTag) -> Contract>(
        "abyssal" to { level, tag -> AbyssalContract.load(tag, ContractSavedData.get(level)) },
        "bound" to { level, tag -> BoundContract.load(tag) }
    )

    fun register(): ArgumentBuilder<CommandSourceStack, *> =
        Commands.literal("load")
            .then(
                Commands.argument("type", ContractTypeArgument.contractType())
                    .then(
                        Commands.argument("jsonString", StringArgumentType.string())
                            .then(
                                Commands.argument("targets", EntityArgument.players())
                                    .executes { context ->
                                        giveContract(
                                            context.source,
                                            loadContract(
                                                StringArgumentType.getString(context, "jsonString"),
                                                context.source.level,
                                                StringArgumentType.getString(context, "type"),
                                            ),
                                            EntityArgument.getPlayer(context, "targets")
                                        )
                                    }).executes { context ->
                                giveContract(
                                    context.source,
                                    loadContract(
                                        StringArgumentType.getString(context, "jsonString"),
                                        context.source.level,
                                        StringArgumentType.getString(context, "type"),
                                    ),
                                    context.source.player
                                )
                            })
            )

    fun loadContract(jsonString: String, level: Level, type: String): Contract {
        try {
            val index = jsonString.toIntOrNull()
            if (index != null) {
                return ContractSavedData.get(level).generator.generateContract(
                    ContractTag(
                        ContractDataReloadListener.data.availableContracts[index].tag.copy()
                    )
                )
            }

            val tag = ContractTag.fromJson(JsonParser.parseString(jsonString).asJsonObject)

            return options[type]!!.invoke(level, tag)
        } catch (e: Error) {
            WingsContractsMod.LOGGER.error(e)
            throw e
        }
    }
}