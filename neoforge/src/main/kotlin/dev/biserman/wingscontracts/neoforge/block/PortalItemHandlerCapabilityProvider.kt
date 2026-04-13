package dev.biserman.wingscontracts.neoforge.block

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.registry.ModBlockRegistry
import net.minecraft.core.Direction
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.items.IItemHandler

@EventBusSubscriber(modid = WingsContractsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
object PortalItemHandlerCapabilityProvider : ICapabilityProvider<ContractPortalBlockEntity, Direction, IItemHandler> {
    override fun getCapability(
        portal: ContractPortalBlockEntity,
        direction: Direction
    ): IItemHandler = ForgePortalItemHandler(portal)

    @SubscribeEvent
    fun registerCapability(event: RegisterCapabilitiesEvent) {
        event.registerBlock(
            Capabilities.ItemHandler.BLOCK,
            { level, pos, _, blockEntity, side ->
                getCapability(
                    blockEntity as ContractPortalBlockEntity,
                    side ?: Direction.UP
                )
            },
            ModBlockRegistry.CONTRACT_PORTAL.get()
        )
    }
}