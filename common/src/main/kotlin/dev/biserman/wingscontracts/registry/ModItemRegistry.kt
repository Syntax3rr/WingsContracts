@file:Suppress("UnstableApiUsage")

package dev.biserman.wingscontracts.registry

import dev.architectury.registry.CreativeTabRegistry
import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.item.BlankAbyssalContractItem
import dev.biserman.wingscontracts.item.BlankBoundContractItem
import dev.biserman.wingscontracts.item.BlankCelestialContractItem
import dev.biserman.wingscontracts.item.ContractItem
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item

object ModItemRegistry {
    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(
        WingsContractsMod.MOD_ID,
        Registries.ITEM
    )

    private val creativeTabKey: ResourceKey<CreativeModeTab> =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("redstone_blocks"))

    val BLANK_ABYSSAL_CONTRACT: RegistrySupplier<Item> = ITEMS.register(
        "blank_abyssal_contract"
    ) { BlankAbyssalContractItem(Item.Properties().stacksTo(1)) }
    val ABYSSAL_CONTRACT: RegistrySupplier<Item> = ITEMS.register(
        "abyssal_contract"
    ) { ContractItem(Item.Properties().stacksTo(1)) }
    val BLANK_BOUND_CONTRACT: RegistrySupplier<Item> = ITEMS.register(
        "blank_bound_contract"
    ) { BlankBoundContractItem(Item.Properties().stacksTo(1)) }
    val BOUND_CONTRACT: RegistrySupplier<Item> = ITEMS.register(
        "bound_contract"
    ) { ContractItem(Item.Properties().stacksTo(1)) }
    val BLANK_CELESTIAL_CONTRACT: RegistrySupplier<Item> = ITEMS.register(
        "blank_celestial_contract"
    ) { BlankCelestialContractItem(Item.Properties().stacksTo(1)) }
    val CELESTIAL_CONTRACT: RegistrySupplier<Item> = ITEMS.register(
        "celestial_contract"
    ) { ContractItem(Item.Properties().stacksTo(1)) }


    val ABYSSAL_COIN: RegistrySupplier<Item> = ITEMS.register(
        "abyssal_coin"
    ) { Item(Item.Properties().stacksTo(64)) }

    val EXCLAMATION_MARK: RegistrySupplier<Item> = ITEMS.register(
        "exclamation_mark"
    ) { Item(Item.Properties().stacksTo(16)) }
    val YELLOW_EXCLAMATION_MARK: RegistrySupplier<Item> = ITEMS.register(
        "yellow_exclamation_mark"
    ) { Item(Item.Properties().stacksTo(16)) }
    val RED_EXCLAMATION_MARK: RegistrySupplier<Item> = ITEMS.register(
        "red_exclamation_mark"
    ) { Item(Item.Properties().stacksTo(16)) }
    val QUESTION_MARK: RegistrySupplier<Item> = ITEMS.register(
        "question_mark"
    ) { Item(Item.Properties().stacksTo(16)) }
    val DISABLED: RegistrySupplier<Item> = ITEMS.register(
        "disabled"
    ) { Item(Item.Properties().stacksTo(16)) }
    val STAR: RegistrySupplier<Item> = ITEMS.register(
        "star"
    ) { Item(Item.Properties().stacksTo(16)) }

    @JvmStatic
    fun register() {
        ITEMS.register()
        CreativeTabRegistry.append(
            creativeTabKey,
            BLANK_ABYSSAL_CONTRACT,
            BLANK_BOUND_CONTRACT,
            BLANK_CELESTIAL_CONTRACT,
            ABYSSAL_COIN,
        )
    }
}
