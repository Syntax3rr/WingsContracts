package dev.biserman.wingscontracts.registry

import com.mojang.brigadier.CommandDispatcher
import dev.architectury.event.events.common.CommandRegistrationEvent
import dev.architectury.registry.registries.DeferredRegister
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.command.ContractTypeArgument
import dev.biserman.wingscontracts.command.ModCommand
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.synchronization.ArgumentTypeInfo
import net.minecraft.commands.synchronization.ArgumentTypeInfos
import net.minecraft.commands.synchronization.SingletonArgumentInfo
import net.minecraft.core.registries.Registries

object ModCommandRegistry {
    val ARGUMENT_TYPES: DeferredRegister<ArgumentTypeInfo<*, *>> = DeferredRegister.create(
        WingsContractsMod.MOD_ID,
        Registries.COMMAND_ARGUMENT_TYPE
    )

    val CONTRACT_TYPE = ARGUMENT_TYPES.register("contract_type") {
        val info = SingletonArgumentInfo.contextFree(ContractTypeArgument::contractType)
        ArgumentTypeInfos.BY_CLASS[ContractTypeArgument::class.java] = info
        info
    }

    @JvmStatic
    fun register() {
        CommandRegistrationEvent.EVENT.register(CommandRegistrationEvent {
            dispatcher: CommandDispatcher<CommandSourceStack>,
            _: CommandBuildContext,
            _: Commands.CommandSelection? ->
                val contractNode = ModCommand.register().build()
                dispatcher.root.addChild(contractNode)
        })

        ARGUMENT_TYPES.register()
    }
}
