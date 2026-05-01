package dev.biserman.wingscontracts.registry

import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.data.CelestialContractLootEntry
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType

object ModLootEntryRegistry {
    val LOOT_POOL_ENTRY_TYPES: DeferredRegister<LootPoolEntryType> = DeferredRegister.create(
        WingsContractsMod.MOD_ID,
        Registries.LOOT_POOL_ENTRY_TYPE
    )

    val CELESTIAL_CONTRACT: RegistrySupplier<LootPoolEntryType> = LOOT_POOL_ENTRY_TYPES.register(
        "celestial_contract"
    ) { LootPoolEntryType(CelestialContractLootEntry.CODEC) }

    @JvmStatic
    fun register() {
        LOOT_POOL_ENTRY_TYPES.register()
    }
}
