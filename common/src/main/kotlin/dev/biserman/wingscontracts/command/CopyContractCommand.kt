package dev.biserman.wingscontracts.command

import com.mojang.brigadier.builder.ArgumentBuilder
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.command.ModCommand.giveContract
import dev.biserman.wingscontracts.core.Contract.Companion.id
import dev.biserman.wingscontracts.data.LoadedContracts
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import java.util.*

object CopyContractCommand {
    fun register(): ArgumentBuilder<CommandSourceStack, *> =
        Commands.literal("copy")
            .requires { context -> context.entity is Player }
            .executes { context -> copyContract(context.source) }

    fun copyContract(sourceStack: CommandSourceStack): Int {
        val player = sourceStack.player
        if (player == null) {
            return 0
        }

        val tag = ContractTagHelper.getContractTag(player.mainHandItem)?.tag?.copy()
        if (tag == null) {
            sourceStack.sendFailure(Component.translatable("commands.${WingsContractsMod.MOD_ID}.failed.not_in_hand"))
            return 0
        }

        val contractTag = ContractTag(tag)
        contractTag.id = UUID.randomUUID()

        return giveContract(
            sourceStack,
            LoadedContracts[contractTag],
            player
        )
    }
}