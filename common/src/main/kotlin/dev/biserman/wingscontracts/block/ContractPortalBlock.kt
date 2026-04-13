@file:Suppress("OVERRIDE_DEPRECATION")

package dev.biserman.wingscontracts.block

import com.mojang.serialization.MapCodec
import dev.architectury.registry.menu.MenuRegistry
import dev.biserman.wingscontracts.block.state.properties.ContractPortalMode
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.item.ContractItem
import dev.biserman.wingscontracts.registry.ModBlockEntityRegistry
import dev.biserman.wingscontracts.registry.ModSoundRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Containers
import net.minecraft.world.InteractionHand
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import kotlin.math.min

class ContractPortalBlock(properties: Properties) : BaseEntityBlock(properties) {
    init {
        getStateDefinition()
        this.registerDefaultState(
            getStateDefinition()
                .any()
                .setValue(MODE, ContractPortalMode.UNLIT)
        )
    }

    override fun useItemOn(
        itemStack: ItemStack,
        blockState: BlockState,
        level: Level,
        blockPos: BlockPos,
        player: Player,
        interactionHand: InteractionHand,
        blockHitResult: BlockHitResult
    ): ItemInteractionResult {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS
        }

        if (blockState.getValue(MODE) == ContractPortalMode.COIN) {
            return ItemInteractionResult.FAIL;
        }

        val portal = level.getBlockEntity(blockPos) as? ContractPortalBlockEntity
            ?: return ItemInteractionResult.FAIL

        val itemInHand = player.getItemInHand(interactionHand)
        val contractSlotItem = portal.contractSlot

        portal.lastPlayer = player.uuid

        if (contractSlotItem.isEmpty) {
            if (itemInHand.item !is ContractItem) {
                val blockEntity = level.getBlockEntity(blockPos)
                if (player is ServerPlayer
                    && blockEntity is ContractPortalBlockEntity
                    && ModConfig.SERVER.abyssalContractsPoolOptions.get() != 0
                ) {
                    MenuRegistry.openMenu(player, blockEntity)
                    return ItemInteractionResult.CONSUME
                } else {
                    return ItemInteractionResult.SUCCESS
                }
            }

            portal.contractSlot = itemInHand
            player.setItemInHand(interactionHand, contractSlotItem)
            level.setBlockAndUpdate(blockPos, blockState.setValue(MODE, ContractPortalMode.LIT))
            level.playSound(null, blockPos, ModSoundRegistry.PORTAL_ADD_CONTRACT.get(), SoundSource.BLOCKS)
        } else {
            if (!itemInHand.isEmpty) {
                return ItemInteractionResult.FAIL
            }

            portal.contractSlot = ItemStack.EMPTY
            player.setItemInHand(interactionHand, contractSlotItem)
            level.setBlockAndUpdate(
                blockPos, blockState.setValue(
                    MODE, if (!portal.cachedRewards.isEmpty || !portal.inputItemsEmpty) {
                        ContractPortalMode.COIN
                    } else {
                        ContractPortalMode.UNLIT
                    }
                )
            )
            level.playSound(null, blockPos, ModSoundRegistry.PORTAL_REMOVE_CONTRACT.get(), SoundSource.BLOCKS)
        }

        level.gameEvent(player, GameEvent.BLOCK_CHANGE, blockPos)
        level.sendBlockUpdated(blockPos, blockState, blockState, UPDATE_ALL)
        return ItemInteractionResult.SUCCESS
    }

    override fun getShape(
        blockState: BlockState,
        blockGetter: BlockGetter,
        blockPos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape = SHAPE

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(MODE)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level, blockState: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return if (level.isClientSide) {
            null
        } else {
            createTickerHelper(
                blockEntityType, ModBlockEntityRegistry.CONTRACT_PORTAL.get(), ContractPortalBlockEntity::serverTick
            )
        }
    }

    override fun newBlockEntity(blockPos: BlockPos, blockState: BlockState): BlockEntity {
        return ContractPortalBlockEntity(blockPos, blockState)
    }

    override fun codec() = CODEC

    override fun getRenderShape(blockState: BlockState): RenderShape {
        return RenderShape.MODEL
    }

    override fun onRemove(
        blockState: BlockState,
        level: Level,
        blockPos: BlockPos,
        blockState2: BlockState,
        bl: Boolean
    ) {
        if (!blockState.`is`(blockState2.block)) {
            val blockEntity = level.getBlockEntity(blockPos)
            if (blockEntity is ContractPortalBlockEntity) {
                Containers.dropContents(level, blockPos, blockEntity.cachedInput)
                Containers.dropItemStack(
                    level,
                    blockPos.x.toDouble(),
                    blockPos.y.toDouble(),
                    blockPos.z.toDouble(),
                    blockEntity.contractSlot
                )
                while (!blockEntity.cachedRewards.isEmpty) {
                    val rewardStackToSpit =
                        ContractSavedData.get(level).currencyHandler.splitHighestDenomination(blockEntity.cachedRewards.items.first { !it.isEmpty })
                    while (rewardStackToSpit.count > 0) {
                        val splitStack = rewardStackToSpit.split(
                            min(
                                level.random.nextInt(21) + 10,
                                rewardStackToSpit.maxStackSize
                            )
                        )
                        Containers.dropItemStack(
                            level,
                            blockPos.x.toDouble(),
                            blockPos.y.toDouble(),
                            blockPos.z.toDouble(),
                            splitStack
                        )
                    }
                }
                level.updateNeighbourForOutputSignal(blockPos, this)
                @Suppress("DEPRECATION")
                super.onRemove(blockState, level, blockPos, blockState2, bl)
            }
        }
    }

    override fun hasAnalogOutputSignal(blockState: BlockState): Boolean = true
    override fun getAnalogOutputSignal(blockState: BlockState, level: Level, blockPos: BlockPos): Int {
        if (blockState.getValue(MODE) == ContractPortalMode.LIT) {
            val portal = level.getBlockEntity(blockPos) as? ContractPortalBlockEntity
                ?: return 0
            return AbstractContainerMenu.getRedstoneSignalFromContainer(portal.cachedRewards)
        }

        return 0
    }

    override fun useShapeForLightOcclusion(blockState: BlockState) = true

    override fun isPathfindable(blockState: BlockState, pathComputationType: PathComputationType) = false

    companion object {
        val CODEC: MapCodec<ContractPortalBlock> = simpleCodec { properties -> ContractPortalBlock(properties) }

        val MODE: EnumProperty<ContractPortalMode> = EnumProperty.create(
            "mode",
            ContractPortalMode::class.java
        )

        private val SHAPE: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0)

        fun getLightLevel(state: BlockState): Int {
            return when (state.getValue(MODE)) {
                ContractPortalMode.UNLIT -> 0
                ContractPortalMode.LIT -> 11
                ContractPortalMode.COIN -> 15
                else -> 0
            }
        }
    }
}
