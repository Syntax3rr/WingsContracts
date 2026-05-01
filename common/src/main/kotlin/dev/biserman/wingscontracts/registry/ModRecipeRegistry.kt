package dev.biserman.wingscontracts.registry

import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.data.ContractRerollRecipe
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType

object ModRecipeRegistry {
    val RECIPE_SERIALIZERS: DeferredRegister<RecipeSerializer<*>> = DeferredRegister.create(
        WingsContractsMod.MOD_ID,
        Registries.RECIPE_SERIALIZER
    )

    val RECIPE_TYPES: DeferredRegister<RecipeType<*>> = DeferredRegister.create(
        WingsContractsMod.MOD_ID,
        Registries.RECIPE_TYPE
    )

    val CONTRACT_REROLL_SERIALIZER: RegistrySupplier<RecipeSerializer<ContractRerollRecipe>> =
        RECIPE_SERIALIZERS.register("contract_reroll") { ContractRerollRecipe.Serializer() }

    @JvmStatic
    fun register() {
        RECIPE_SERIALIZERS.register()
        RECIPE_TYPES.register()
    }
}
