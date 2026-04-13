package dev.biserman.wingscontracts.neoforge.client

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.core.Contract
import dev.biserman.wingscontracts.registry.ModItemRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.IItemDecorator
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent

class ContractItemDecorator : IItemDecorator {
    override fun render(
        graphics: GuiGraphics, font: Font, itemStack: ItemStack, x: Int, y: Int
    ): Boolean {
        val minecraft = Minecraft.getInstance()
        val showItem = Contract.Companion.getDisplayItem(itemStack, minecraft.level?.gameTime?.toFloat() ?: 0f)
        if (showItem.isEmpty) {
            return false
        }

        val poseStack = graphics.pose()
        poseStack.pushPose()
        poseStack.translate(
            (x + 4).toFloat(), (y + 4).toFloat(), 100f
        )
        poseStack.scale(0.5f, 0.5f, 0.5f)
        graphics.renderItem(showItem, 0, 0)
        poseStack.popPose()

        return false
    }

    @EventBusSubscriber(modid = WingsContractsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
    companion object {
        val instance = ContractItemDecorator()

        @JvmStatic
        @SubscribeEvent
        fun registerItemDecorations(event: RegisterItemDecorationsEvent) {
            val abyssalContract = ModItemRegistry.ABYSSAL_CONTRACT.get()
            val boundContract = ModItemRegistry.BOUND_CONTRACT.get()

            if (abyssalContract == null) {
                WingsContractsMod.LOGGER.error("Unable to register item decorations: abyssalContract could not be found.")
            }

            if (boundContract == null) {
                WingsContractsMod.LOGGER.error("Unable to register item decorations: boundContract could not be found.")
            }

            event.register(abyssalContract, instance)
            event.register(boundContract, instance)
        }
    }
}