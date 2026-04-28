package dev.biserman.wingscontracts.data

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import dev.biserman.wingscontracts.registry.ModLootEntryRegistry
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer
import net.minecraft.world.level.storage.loot.functions.LootItemFunction
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import java.util.function.Consumer

class CelestialContractLootEntry(
    weight: Int,
    quality: Int,
    conditions: List<LootItemCondition>,
    functions: List<LootItemFunction>,
) : LootPoolSingletonContainer(weight, quality, conditions, functions) {

    override fun getType(): LootPoolEntryType = ModLootEntryRegistry.CELESTIAL_CONTRACT.get()

    override fun createItemStack(consumer: Consumer<ItemStack>, context: LootContext) {
        val level = context.level ?: return
        val contract = ContractSavedData.get(level).celestialGenerator.generateContract() ?: return
        contract.initialize()
        consumer.accept(contract.createItem())
    }

    companion object {
        val CODEC: MapCodec<CelestialContractLootEntry> = RecordCodecBuilder.mapCodec { instance ->
            singletonFields(instance).apply(instance, ::CelestialContractLootEntry)
        }
    }
}
