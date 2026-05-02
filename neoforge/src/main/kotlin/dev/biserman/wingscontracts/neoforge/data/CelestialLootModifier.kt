package dev.biserman.wingscontracts.neoforge.data

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import dev.biserman.wingscontracts.data.CelestialLootInjector
import dev.biserman.wingscontracts.data.ContractSavedData
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.neoforged.neoforge.common.loot.IGlobalLootModifier
import net.neoforged.neoforge.common.loot.LootModifier

class CelestialLootModifier(conditions: Array<LootItemCondition>) : LootModifier(conditions) {

    override fun doApply(
        generatedLoot: ObjectArrayList<ItemStack>,
        context: LootContext,
    ): ObjectArrayList<ItemStack> {
        if (!CelestialLootInjector.shouldInject(context.queriedLootTableId)) return generatedLoot
        val contract = ContractSavedData.get(context.level).celestialGenerator.generateContract()
            ?: return generatedLoot
        generatedLoot.add(contract.createItem())
        return generatedLoot
    }

    override fun codec(): MapCodec<out IGlobalLootModifier> = CODEC

    companion object {
        val CODEC: MapCodec<CelestialLootModifier> = RecordCodecBuilder.mapCodec { instance ->
            codecStart(instance).apply(instance, ::CelestialLootModifier)
        }
    }
}
