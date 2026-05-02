package dev.biserman.wingscontracts.neoforge

import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.core.Contract.Companion.type
import dev.biserman.wingscontracts.core.ContractType
import dev.biserman.wingscontracts.data.CelestialReroll
import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.data.RerollDirection
import dev.biserman.wingscontracts.data.RerollResult
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import dev.biserman.wingscontracts.registry.ModItemRegistry
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import java.util.Collections
import java.util.WeakHashMap

object CelestialRerollEvents {
    // Per-ItemEntity flag: once set, we don't re-trigger fire reroll until the entity is GC'd
    // (i.e. the player picks up the stack and re-tosses it as a fresh entity).
    private val rerolledByFire: MutableSet<ItemEntity> =
        Collections.newSetFromMap(WeakHashMap())

    fun register(bus: IEventBus) {
        bus.addListener<EntityStruckByLightningEvent>(this::onLightning)
        bus.addListener<EntityTickEvent.Post>(this::onEntityTickPost)
    }

    private fun onLightning(event: EntityStruckByLightningEvent) {
        if (!ModConfig.SERVER.celestialEnableLightningReroll.get()) return
        val item = event.entity as? ItemEntity ?: return
        if (!isCelestialContractItem(item)) return
        // Cancel the strike's damage/fire effects on any celestial contract item, blank or activated.
        // Disabling the reroll feature opts out of this protection too: packs that turn off the
        // feature presumably want vanilla lightning behavior.
        event.isCanceled = true

        val level = item.level() as? ServerLevel ?: return
        if (!isCelestial(item)) return

        val stack = item.item.copy()
        val data = ContractSavedData.get(level)
        val result = CelestialReroll.applyReroll(stack, RerollDirection.RELAX, data)
        if (result is RerollResult.Success) {
            forceSyncItem(item, stack)
            level.playSound(
                null, item.x, item.y, item.z,
                SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1f, 1.5f
            )
        }
    }

    private fun onEntityTickPost(event: EntityTickEvent.Post) {
        if (!ModConfig.SERVER.celestialEnableFireReroll.get()) return
        val item = event.entity as? ItemEntity ?: return
        // Cheap filters first: every dropped item in the world ticks here, so we want to bail
        // before the bounding-box block walk in isInFireOrLava.
        if (!isCelestialContractItem(item)) return
        if (item in rerolledByFire) return
        if (!isCelestial(item)) return
        val level = item.level() as? ServerLevel ?: return
        if (!isInFireOrLava(item)) return

        val stack = item.item.copy()
        val data = ContractSavedData.get(level)
        val result = CelestialReroll.applyReroll(stack, RerollDirection.RESTRICT, data)
        rerolledByFire += item

        if (result is RerollResult.Success) {
            forceSyncItem(item, stack)
            val v = item.deltaMovement
            item.deltaMovement = Vec3(v.x, 0.5, v.z)
            level.playSound(
                null, item.x, item.y, item.z,
                SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1f, 0.8f
            )
        }
    }

    // SynchedEntityData.set's default equality check sometimes considers the modified stack equal to
    // the live stack (CustomData.equals walks the underlying tag, and our patch swap can land at the
    // same content), so we force the dirty-mark to guarantee the client gets a fresh packet.
    private fun forceSyncItem(item: ItemEntity, stack: ItemStack) {
        item.entityData.set(ItemEntity.DATA_ITEM, stack, true)
    }

    private fun isInFireOrLava(item: ItemEntity): Boolean {
        if (item.isInLava) return true
        // Walk every block the entity's bounding box overlaps so we catch items resting on top
        // of campfires/fire blocks, not just the one block at item position.
        val level = item.level()
        val box = item.boundingBox.deflate(0.001)
        val min = BlockPos.containing(box.minX, box.minY - 0.05, box.minZ)
        val max = BlockPos.containing(box.maxX, box.maxY, box.maxZ)
        for (pos in BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).`is`(BlockTags.FIRE)) return true
        }
        return false
    }

    private fun isCelestialContractItem(item: ItemEntity): Boolean {
        val it = item.item.item
        return it == ModItemRegistry.CELESTIAL_CONTRACT.get() || it == ModItemRegistry.BLANK_CELESTIAL_CONTRACT.get()
    }

    private fun isCelestial(item: ItemEntity): Boolean {
        val tag = ContractTagHelper.getContractTag(item.item) ?: return false
        return tag.type == ContractType.CELESTIAL
    }
}
