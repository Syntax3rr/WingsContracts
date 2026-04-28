package dev.biserman.wingscontracts.item

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.registry.ModSoundRegistry
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level

class BlankCelestialContractItem(properties: Properties) : Item(properties) {
    override fun use(
        level: Level,
        player: Player,
        interactionHand: InteractionHand
    ): InteractionResultHolder<ItemStack> {
        val itemStack = player.getItemInHand(interactionHand)
        if (!ModConfig.SERVER.allowBlankCelestialContractUse.get()) {
            return InteractionResultHolder.pass(itemStack)
        }
        if (level !is ServerLevel) {
            player.playSound(ModSoundRegistry.WRITE_CONTRACT.get())
            return InteractionResultHolder.success(itemStack)
        }

        val contract = ContractSavedData.get(level).celestialGenerator.generateContract() ?: run {
            player.displayClientMessage(
                Component.translatable("item.${WingsContractsMod.MOD_ID}.blank_celestial_contract.empty_pool")
                    .withStyle(ChatFormatting.RED),
                true,
            )
            return InteractionResultHolder.fail(itemStack)
        }
        contract.initialize()
        val newContractStack = contract.createItem()
        player.setItemInHand(interactionHand, newContractStack)
        return InteractionResultHolder.success(newContractStack)
    }

    override fun appendHoverText(
        itemStack: ItemStack,
        tooltipContext: TooltipContext,
        components: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        if (ModConfig.SERVER.allowBlankCelestialContractUse.get()) {
            components.add(
                Component
                    .translatable("item.${WingsContractsMod.MOD_ID}.blank_celestial_contract.desc.can")
                    .withStyle(ChatFormatting.GRAY)
            )
        } else {
            components.add(
                Component
                    .translatable("item.${WingsContractsMod.MOD_ID}.blank_celestial_contract.desc.cannot")
                    .withStyle(ChatFormatting.GRAY)
            )
        }
    }
}
