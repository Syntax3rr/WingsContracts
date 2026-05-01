package dev.biserman.wingscontracts.command

import com.mojang.brigadier.builder.ArgumentBuilder
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.data.CelestialReroll
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.data.RerollDirection
import dev.biserman.wingscontracts.data.RerollResult
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object RerollContractCommand {
    fun register(): ArgumentBuilder<CommandSourceStack, *> =
        Commands.literal("reroll")
            .then(
                Commands.literal("relax")
                    .then(
                        Commands.argument("target", EntityArgument.player())
                            .executes { ctx -> reroll(ctx.source, EntityArgument.getPlayer(ctx, "target"), RerollDirection.RELAX) })
                    .executes { ctx -> reroll(ctx.source, ctx.source.player, RerollDirection.RELAX) }
            )
            .then(
                Commands.literal("restrict")
                    .then(
                        Commands.argument("target", EntityArgument.player())
                            .executes { ctx -> reroll(ctx.source, EntityArgument.getPlayer(ctx, "target"), RerollDirection.RESTRICT) })
                    .executes { ctx -> reroll(ctx.source, ctx.source.player, RerollDirection.RESTRICT) }
            )

    private fun reroll(source: CommandSourceStack, target: ServerPlayer?, direction: RerollDirection): Int {
        if (target == null) {
            source.sendFailure(Component.translatable("commands.${WingsContractsMod.MOD_ID}.failed.only_one_player"))
            return 0
        }

        val stack = target.mainHandItem
        val data = ContractSavedData.get(source.level)
        val result = CelestialReroll.applyReroll(stack, direction, data)

        when (result) {
            is RerollResult.Success -> {
                source.sendSuccess({
                    Component.translatable(
                        "commands.${WingsContractsMod.MOD_ID}.reroll.success",
                        target.name.string,
                        result.stat.name.lowercase(),
                        direction.name.lowercase(),
                    )
                }, true)
                return 1
            }
            RerollResult.NotACelestialContract -> {
                source.sendFailure(Component.translatable("commands.${WingsContractsMod.MOD_ID}.reroll.not_celestial"))
            }
            RerollResult.AlreadyUsed -> {
                source.sendFailure(Component.translatable("commands.${WingsContractsMod.MOD_ID}.reroll.already_used"))
            }
            RerollResult.MaxRerollsReached -> {
                source.sendFailure(Component.translatable("commands.${WingsContractsMod.MOD_ID}.reroll.max_rerolls_reached"))
            }
            RerollResult.NoApplicableStat -> {
                source.sendFailure(Component.translatable("commands.${WingsContractsMod.MOD_ID}.reroll.no_applicable_stat"))
            }
        }
        return 0
    }
}
