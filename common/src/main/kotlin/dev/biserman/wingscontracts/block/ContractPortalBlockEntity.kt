@file:OptIn(ExperimentalStdlibApi::class)

package dev.biserman.wingscontracts.block

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.advancements.ContractCompleteTrigger
import dev.biserman.wingscontracts.block.ContractPortalBlock.Companion.MODE
import dev.biserman.wingscontracts.block.state.properties.ContractPortalMode
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.container.CompactingContainer
import dev.biserman.wingscontracts.core.AbyssalContract
import dev.biserman.wingscontracts.core.BoundContract
import dev.biserman.wingscontracts.core.Contract
import dev.biserman.wingscontracts.core.PortalLinker
import dev.biserman.wingscontracts.data.ContractDataReloadListener
import dev.biserman.wingscontracts.data.LoadedContracts
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import dev.biserman.wingscontracts.registry.ModBlockEntityRegistry
import dev.biserman.wingscontracts.registry.ModBlockRegistry
import dev.biserman.wingscontracts.registry.ModMenuRegistry
import dev.biserman.wingscontracts.registry.ModSoundRegistry
import dev.biserman.wingscontracts.scoreboard.ScoreboardHandler
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth.*
import net.minecraft.world.*
import net.minecraft.world.entity.EntitySelector
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock.UPDATE_ALL
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.*
import java.util.stream.Collectors
import kotlin.math.max
import kotlin.math.min

class ContractPortalBlockEntity(
    blockPos: BlockPos,
    blockState: BlockState,
) :
    BlockEntity(ModBlockEntityRegistry.CONTRACT_PORTAL.get(), blockPos, blockState),
    MenuProvider,
    WorldlyContainer {
    var cooldownTime: Int
    var contractSlot: ItemStack = ItemStack.EMPTY
        set(value) {
            val currentLevel = level
            if (currentLevel != null) {
                val oldContractId = LoadedContracts[field]?.id
                val newContractId = LoadedContracts[value]?.id

                PortalLinker.get(currentLevel).linkedPortals.remove(oldContractId)
                if (newContractId != null) {
                    PortalLinker.get(currentLevel).linkedPortals[newContractId] = this
                }
            }
            field = value
        }
    var cachedRewards = CompactingContainer(inputSlotsCount)
    var cachedInput = SimpleContainer(inputSlotsCount)
    var lastPlayer: UUID

    private fun getLevelX(): Double = worldPosition.x.toDouble() + 0.5
    private fun getLevelY(): Double = worldPosition.y.toDouble() + 0.5
    private fun getLevelZ(): Double = worldPosition.z.toDouble() + 0.5

    init {
        this.cooldownTime = -1
        this.lastPlayer = UUID(0, 0)
    }

    override fun setLevel(level: Level) {
        super.setLevel(level)
        val contract = LoadedContracts[contractSlot] ?: return
        PortalLinker.get(level).linkedPortals[contract.id] = this
    }

    override fun setRemoved() {
        super.setRemoved()
        val contractId = LoadedContracts[contractSlot]?.id
        PortalLinker.get(level ?: return).linkedPortals.remove(contractId)
    }

    override fun loadAdditional(compoundTag: CompoundTag, provider: HolderLookup.Provider) {
        this.cooldownTime = compoundTag.getInt("SuckCooldown")
        this.contractSlot = ItemStack.parseOptional(provider, compoundTag.getCompound("ContractSlot"))
        this.lastPlayer = compoundTag.getUUID("LastPlayer")

        loadAllItems(compoundTag.getCompound("Items"), cachedInput.items, provider)
        loadAllItems(compoundTag.getCompound("Rewards"), cachedRewards.items, provider)

        super.loadAdditional(compoundTag, provider)
    }

    @Suppress("KotlinConstantConditions")
    fun loadAllItems(
        containerTag: CompoundTag,
        containerList: NonNullList<ItemStack>,
        provider: HolderLookup.Provider
    ) {
        val listTag: ListTag = containerTag.getList("Items", 10)

        for (i in listTag.indices) {
            val compoundTag2 = listTag.getCompound(i)
            val count = compoundTag2.getByte("Slot").toInt() and 255
            if (count >= 0 && count < containerList.size) {
                containerList[count] = ItemStack.parseOptional(provider, compoundTag2)
            }
        }
    }

    fun saveAllItems(containerList: NonNullList<ItemStack>, provider: HolderLookup.Provider): CompoundTag {
        val listTag = ListTag()

        for (i in containerList.indices) {
            val itemStack = containerList[i]
            if (!itemStack.isEmpty) {
                val slotTag = CompoundTag()
                slotTag.putByte("Slot", i.toByte())
                listTag.add(itemStack.save(provider, slotTag))
            }
        }

        val containerTag = CompoundTag()
        if (!listTag.isEmpty()) {
            containerTag.put("Items", listTag)
        }

        return containerTag
    }

    override fun saveAdditional(compoundTag: CompoundTag, provider: HolderLookup.Provider) {
        super.saveAdditional(compoundTag, provider)
        compoundTag.putInt("SuckCooldown", this.cooldownTime)
        compoundTag.putUUID("LastPlayer", this.lastPlayer)

        if (contractSlot != ItemStack.EMPTY) {
            val contractSlotTag = CompoundTag()
            compoundTag.put("ContractSlot", contractSlot.save(provider, contractSlotTag))
        }

        val cachedInputsTag = saveAllItems(cachedInput.items, provider)
        val cachedRewardsTag = saveAllItems(cachedRewards.items, provider)

        compoundTag.put("Items", cachedInputsTag)
        compoundTag.put("Rewards", cachedRewardsTag)
    }

    override fun getUpdateTag(provider: HolderLookup.Provider): CompoundTag = this.saveWithoutMetadata(provider)

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? = ClientboundBlockEntityDataPacket.create(this)

    private fun inventoryFull(): Boolean {
        val iterator: Iterator<ItemStack> = cachedInput.items.iterator()

        var itemStack: ItemStack
        do {
            if (!iterator.hasNext()) {
                return true
            }

            itemStack = iterator.next()
        } while (!itemStack.isEmpty && itemStack.count == itemStack.maxStackSize)

        return false
    }

    private fun getInputItem(i: Int): ItemStack = cachedInput.getItem(i)
    private fun setInputItem(i: Int, itemStack: ItemStack) {
        cachedInput.setItem(i, itemStack)
        if (itemStack.count > MAX_STACK_SIZE) {
            itemStack.count = MAX_STACK_SIZE
        }
        this.setChanged()
    }

    val inputItemsEmpty get() = cachedInput.isEmpty || cachedInput.items.all { itemStack -> itemStack.isEmpty }

    fun updateMode(mode: ContractPortalMode) {
        level?.setBlockAndUpdate(blockPos, blockState.setValue(MODE, mode))
        level?.gameEvent(null, GameEvent.BLOCK_CHANGE, blockPos)
        level?.sendBlockUpdated(blockPos, blockState, blockState, UPDATE_ALL)
    }

    val isPowered get() = level?.hasNeighborSignal(blockPos) == true

    companion object {
        val STORAGE_ID: ResourceLocation =
            ResourceLocation.parse("${ModBlockEntityRegistry.CONTRACT_PORTAL.id}_storage")

        fun serverTick(
            level: Level, blockPos: BlockPos, blockState: BlockState,
            portal: ContractPortalBlockEntity
        ): Boolean {
            --portal.cooldownTime
            if (portal.cooldownTime > 0) {
                return false
            }
            portal.cooldownTime = (0)

            when (portal.blockState.getValue(MODE)) {
                ContractPortalMode.UNLIT -> {}
                ContractPortalMode.LIT -> {
                    val contractTag = ContractTagHelper.getContractTag(portal.contractSlot) ?: return false
                    val contract = LoadedContracts[contractTag] ?: return false

                    if (contract is BoundContract) {
                        val linkedPortal = contract.getLinkedPortal(level)
                        if (linkedPortal == null) {
                            portal.updateMode(ContractPortalMode.NOT_CONNECTED)
                            return true
                        }

                        if (linkedPortal.lastPlayer == portal.lastPlayer
                            && ModConfig.SERVER.boundContractRequiresTwoPlayers.get()
                        ) {
                            portal.updateMode(ContractPortalMode.ERROR)
                            return true
                        }
                    }

                    val didConsume = portal.tryConsume(contract, contractTag)
                    val didSuck = portal.suckInItems(contract, level)
                    val didUpdate = contract.tryUpdateTick(contractTag)

                    if (didConsume && contract is AbyssalContract && contract.isComplete) {
                        playSound(level, blockPos, ModSoundRegistry.COMPLETE_CONTRACT.get())
                    }

                    if (didUpdate) {
                        level.sendBlockUpdated(blockPos, blockState, blockState, UPDATE_ALL)
                    }

                    if (didConsume || didSuck || didUpdate) {
                        portal.cooldownTime = (5)
                        setChanged(level, blockPos, blockState)
                        return true
                    } else {
                        portal.cooldownTime = (10)
                    }
                }

                ContractPortalMode.COIN -> {
                    val stackToSpit = when {
                        !portal.cachedRewards.isEmpty ->
                            portal.cachedRewards.items.first { !it.isEmpty }

                        !portal.cachedInput.isEmpty ->
                            portal.cachedInput.items.firstOrNull { itemStack -> !itemStack.isEmpty }

                        else -> null
                    }

                    if (stackToSpit == null) {
                        portal.updateMode(
                            when (portal.contractSlot.isEmpty) {
                                true -> ContractPortalMode.UNLIT
                                false -> ContractPortalMode.LIT
                            }
                        )
                        return true
                    }

                    if (portal.cachedRewards.items.sumOf { it.count } > stackToSpit.maxStackSize
                        || portal.cachedInput.items.sumOf { it.count } > stackToSpit.maxStackSize) {
                        spitItemStack(stackToSpit, level, blockPos, amountToSpit = stackToSpit.count)
                    } else {
                        spitItemStack(stackToSpit, level, blockPos, amountToSpit = max(4, stackToSpit.count / 2))
                    }

                    playSound(level, blockPos, ModSoundRegistry.PORTAL_SPIT.get())

                    portal.cooldownTime = (10)
                    return true
                }

                else -> {
                    val contract = LoadedContracts[portal.contractSlot] as? BoundContract

                    if (contract == null) {
                        portal.updateMode(ContractPortalMode.UNLIT)
                        return true
                    }

                    val linkedPortal = contract.getLinkedPortal(level) ?: return false

                    if (linkedPortal.lastPlayer == portal.lastPlayer
                        && ModConfig.SERVER.boundContractRequiresTwoPlayers.get()
                    ) {
                        return false
                    }

                    portal.updateMode(ContractPortalMode.LIT)
                    return true
                }
            }

            return false
        }

        private fun playSound(level: Level, blockPos: BlockPos, sound: SoundEvent) {
            if (sequenceOf(
                    blockPos.below(),
                    blockPos.north(),
                    blockPos.east(),
                    blockPos.south(),
                    blockPos.west()
                ).all { !level.getBlockState(it).`is`(BlockTags.WOOL) }
            ) {
                level.playSound(null, blockPos, sound, SoundSource.BLOCKS)
            }
        }

        fun spitItemStack(
            stackToSpit: ItemStack,
            level: Level,
            blockPos: BlockPos,
            amountToSpit: Int
        ) {
            val splitStack = stackToSpit.split(min(min(amountToSpit, stackToSpit.count), stackToSpit.maxStackSize))
            spawnItem(splitStack, level, blockPos)
        }

        private fun spawnItem(itemStack: ItemStack, level: Level, blockPos: BlockPos) {
            val itemEntity = ItemEntity(level, blockPos.x + 0.5, blockPos.y + 0.75, blockPos.z + 0.5, itemStack)
            val magnitude = 0.15
            val radians = level.random.nextFloat() * PI * 2
            itemEntity.setDeltaMovement(sin(radians) * magnitude, magnitude * 3, cos(radians) * magnitude)
            level.addFreshEntity(itemEntity)
        }

        private fun canMergeItems(itemStack: ItemStack, itemStack2: ItemStack): Boolean {
            return itemStack.count <= itemStack.maxStackSize
                    && ItemStack.isSameItemSameComponents(itemStack, itemStack2)
        }

        private val INSIDE: VoxelShape = Block.box(2.0, 11.0, 2.0, 14.0, 16.0, 14.0)
        private val ABOVE: VoxelShape = Block.box(0.0, 16.0, 0.0, 16.0, 32.0, 16.0)
        private val SUCK: VoxelShape = Shapes.or(INSIDE, ABOVE)
        val inputSlotsCount = ModConfig.SERVER.contractPortalInputSlots.get() ?: 27
        private const val MAX_STACK_SIZE = 64 // not sure why this is needed. might delete
        private fun getSuckShape(): VoxelShape = SUCK
    }

    private fun suckInItems(contract: Contract, level: Level): Boolean {
        val aboveItems = getItemsAtAndAbove(level).iterator()

        if (inventoryFull()) {
            return false
        }

        while (aboveItems.hasNext()) {
            val itemEntity = aboveItems.next()

            if (contract.matches(itemEntity.item)) {
                if (addInputItem(itemEntity)) {
                    playSound(level, blockPos, ModSoundRegistry.PORTAL_ACCEPT.get())
                    return true
                }
            } else {
                spitItemStack(itemEntity.item, level, blockPos, itemEntity.item.count)
                playSound(level, blockPos, ModSoundRegistry.PORTAL_REJECT.get())
            }
        }

        return false
    }

    private fun addInputItem(itemEntity: ItemEntity): Boolean {
        var didConsumeAll = false
        val beforeStack = itemEntity.item.copy()
        val afterStack = addInputItem(beforeStack)
        if (afterStack.isEmpty) {
            didConsumeAll = true
            itemEntity.discard()
        } else {
            itemEntity.item = afterStack
        }

        return didConsumeAll
    }

    private fun addInputItem(itemStack: ItemStack): ItemStack {
        var mutItemStack = itemStack
        val size = inputSlotsCount

        var i = 0
        while (i < size && !mutItemStack.isEmpty) {
            mutItemStack = tryMoveInInputItem(mutItemStack, i)
            ++i
        }

        return mutItemStack
    }

    fun tryMoveInInputItem(itemStack: ItemStack, i: Int, simulate: Boolean = false): ItemStack {
        var mutItemStack = itemStack
        val stackAtSlot = getInputItem(i)
        var movedItems = false
        if (stackAtSlot.isEmpty) {
            if (!simulate) {
                setInputItem(i, mutItemStack)
            }
            mutItemStack = ItemStack.EMPTY
            movedItems = true
        } else if (canMergeItems(stackAtSlot, mutItemStack)) {
            val j = mutItemStack.maxStackSize - stackAtSlot.count
            val k = min(mutItemStack.count.toDouble(), j.toDouble()).toInt()
            mutItemStack.shrink(k)
            if (!simulate) {
                stackAtSlot.grow(k)
            }
            movedItems = k > 0
        }

        if (movedItems && !simulate) {
            cooldownTime = 10
        }

        setChanged()

        return mutItemStack
    }

    private fun tryConsume(contract: Contract, contractTag: ContractTag): Boolean {
        val level = this.level ?: return false

        if (isPowered) {
            return false
        }

        val rewards = contract.tryConsumeFromItems(contractTag, this)

        if (rewards.isEmpty()) {
            return false
        }

        for (itemStack in rewards) {
            cachedRewards.addItem(itemStack)
        }

        val serverLevel = level as? ServerLevel
        if (contract is AbyssalContract && serverLevel != null) {
            val player = serverLevel.getPlayerByUUID(lastPlayer) as? ServerPlayer
            if (player == null) {
                WingsContractsMod.LOGGER.warn("Contract Portal $blockPos could not find last player with UUID $lastPlayer")
            } else {
                ScoreboardHandler.add(
                    serverLevel,
                    player,
                    rewards.sumOf { floor(ContractDataReloadListener.data.valueReward(it)) }
                )

                if (contract.unitsFulfilled >= contract.unitsDemanded) {
                    ContractCompleteTrigger.INSTANCE.trigger(player, contractSlot)
                }
            }
        }

        return true
    }

    private fun getItemsAtAndAbove(level: Level): List<ItemEntity> {
        return getSuckShape().toAabbs().stream().flatMap { aABB: AABB ->
            level.getEntitiesOfClass(
                ItemEntity::class.java,
                aABB.move(getLevelX() - 0.5, getLevelY() - 0.5, getLevelZ() - 0.5),
                EntitySelector.ENTITY_STILL_ALIVE
            ).stream()
        }.collect(Collectors.toList())
    }

    override fun getDisplayName(): Component? = ModBlockRegistry.CONTRACT_PORTAL.get()?.name

    override fun createMenu(
        i: Int,
        inventory: Inventory,
        player: Player
    ): AbstractContainerMenu = ModMenuRegistry.CONTRACT_PORTAL.get().create(i, inventory)


    override fun getSlotsForFace(direction: Direction): IntArray {
        return when (direction) {
            Direction.DOWN -> (inputSlotsCount..<containerSize).toList().toIntArray()
            else -> (0..<inputSlotsCount).toList().toIntArray()
        }
    }

    override fun canPlaceItemThroughFace(
        i: Int,
        itemStack: ItemStack,
        direction: Direction?
    ): Boolean = canPlaceItem(i, itemStack)

    override fun canTakeItemThroughFace(
        i: Int,
        itemStack: ItemStack,
        direction: Direction
    ): Boolean = canTakeItem(i)

    override fun canPlaceItem(i: Int, itemStack: ItemStack): Boolean =
        i < inputSlotsCount && LoadedContracts[contractSlot]?.matches(itemStack) == true

    override fun canTakeItem(container: Container, i: Int, itemStack: ItemStack) = canTakeItem(i)
    fun canTakeItem(i: Int) = i >= inputSlotsCount

    override fun getContainerSize(): Int = inputSlotsCount * 2

    override fun isEmpty(): Boolean = cachedInput.items.all { it.isEmpty } && cachedRewards.isEmpty

    override fun getItem(i: Int): ItemStack {
        return if (i < inputSlotsCount) cachedInput.getItem(i) else cachedRewards.getItem(i - inputSlotsCount)
    }

    override fun removeItem(i: Int, count: Int) = removeItem(i, count, false)
    fun removeItem(i: Int, count: Int, simulate: Boolean = false): ItemStack {
        if (i !in 0..<containerSize || count <= 0) {
            return ItemStack.EMPTY
        }

        return if (i < inputSlotsCount) {
            if (simulate) {
                val itemStack = cachedInput.items[i]
                itemStack.copyWithCount(min(itemStack.count, count))
            } else {
                cachedInput.items[i].split(count)
            }
        } else {
            if (simulate) {
                val itemStack = cachedRewards.items[i - inputSlotsCount]
                itemStack.copyWithCount(min(itemStack.count, count))
            } else {
                cachedRewards.items[i - inputSlotsCount].split(count)
            }
        }
    }

    override fun removeItemNoUpdate(i: Int): ItemStack {
        if (i !in 0..<containerSize) {
            return ItemStack.EMPTY
        }

        return if (i < inputSlotsCount) {
            ContainerHelper.takeItem(cachedInput.items, i)
        } else {
            ContainerHelper.takeItem(cachedRewards.items, i - inputSlotsCount)
        }
    }

    override fun setItem(i: Int, itemStack: ItemStack) {
        if (i < inputSlotsCount) {
            cachedInput.setItem(i, itemStack)
        } else {
            cachedRewards.setItem(i - inputSlotsCount, itemStack)
        }
    }

    override fun setChanged() {
        super.setChanged()
    }

    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)

    override fun clearContent() {
        cachedInput.clearContent()
        cachedRewards.clearContent()
    }
}
