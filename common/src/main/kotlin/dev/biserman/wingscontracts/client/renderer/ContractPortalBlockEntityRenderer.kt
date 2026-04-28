package dev.biserman.wingscontracts.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import dev.biserman.wingscontracts.block.ContractPortalBlock
import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.block.state.properties.ContractPortalMode
import dev.biserman.wingscontracts.core.Contract
import dev.biserman.wingscontracts.data.LoadedContracts
import dev.biserman.wingscontracts.registry.ModItemRegistry
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.level.LightLayer

class ContractPortalBlockEntityRenderer(private val context: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<ContractPortalBlockEntity> {
    override fun render(
        portal: ContractPortalBlockEntity, partialTick: Float, poseStack: PoseStack,
        multiBufferSource: MultiBufferSource, packedLight: Int, packedOverlay: Int
    ) {
        val level = portal.level ?: return

        val blockPos = portal.blockPos.above()
        val relativeGameTime = level.gameTime + partialTick
        val rotation = relativeGameTime * 2

        val contract = LoadedContracts[portal.contractSlot] ?: return
        val mode = portal.blockState.getValue(ContractPortalBlock.MODE)
        val showItem = when {
            contract.isComplete -> ModItemRegistry.STAR.get().defaultInstance
            contract.isDisabled -> ModItemRegistry.DISABLED.get().defaultInstance
            mode == ContractPortalMode.NOT_CONNECTED -> ModItemRegistry.YELLOW_EXCLAMATION_MARK.get().defaultInstance
            mode == ContractPortalMode.ERROR -> ModItemRegistry.RED_EXCLAMATION_MARK.get().defaultInstance
            level.hasNeighborSignal(blockPos) -> ModItemRegistry.EXCLAMATION_MARK.get().defaultInstance
            else -> Contract.getPortalDisplayItem(portal.contractSlot, relativeGameTime)
        }
        if (showItem.isEmpty) {
            return
        }

        poseStack.pushPose()
        poseStack.translate(0.5, 1.6, 0.5)
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation))
        context.itemRenderer.renderStatic(
            showItem, ItemDisplayContext.GROUND,
            LightTexture.pack(
                level.getBrightness(LightLayer.BLOCK, blockPos),
                level.getBrightness(LightLayer.SKY, blockPos)
            ),
            OverlayTexture.NO_OVERLAY, poseStack, multiBufferSource, level, 0
        )

        poseStack.popPose()
    }
}
