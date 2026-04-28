package dev.biserman.wingscontracts.data

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.config.ModConfig
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue

object CelestialLootInjector {

    private fun configuredTables(): List<String>? = try {
        val raw = ModConfig.SERVER.celestialLootInjectionTables.get()
        if (raw.isBlank()) emptyList()
        else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    } catch (_: IllegalStateException) {
        null
    }

    private fun configuredWeight(): Int = try {
        ModConfig.SERVER.celestialLootInjectionWeight.get().coerceAtLeast(1)
    } catch (_: IllegalStateException) {
        1
    }

    fun shouldInject(tableId: ResourceLocation): Boolean {
        val ids = configuredTables() ?: run {
            WingsContractsMod.LOGGER.debug(
                "Skipping celestial loot injection for {}. Server config not yet loaded.", tableId
            )
            return false
        }
        if (ids.isEmpty()) return false
        val target = tableId.toString()
        return ids.any { it == target }
    }

    fun buildPool(): LootPool.Builder = LootPool.lootPool()
        .setRolls(ConstantValue.exactly(1f))
        .add(LootPoolSingletonContainer.simpleBuilder { w, q, conds, funcs ->
            CelestialContractLootEntry(w, q, conds, funcs)
        }.setWeight(configuredWeight()))
}
