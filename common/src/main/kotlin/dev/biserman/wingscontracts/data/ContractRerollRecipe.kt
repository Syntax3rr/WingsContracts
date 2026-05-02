package dev.biserman.wingscontracts.data

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.registry.ModRecipeRegistry
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.level.Level

class ContractRerollRecipe(
    private val recipeGroup: String,
    private val recipeCategory: CraftingBookCategory,
    val direction: RerollDirection,
    private val recipeIngredients: NonNullList<Ingredient>,
) : CraftingRecipe {
    override fun getGroup(): String = recipeGroup
    override fun category(): CraftingBookCategory = recipeCategory
    override fun getIngredients(): NonNullList<Ingredient> = recipeIngredients
    override fun getSerializer(): RecipeSerializer<*> = ModRecipeRegistry.CONTRACT_REROLL_SERIALIZER.get()
    override fun getResultItem(registries: HolderLookup.Provider): ItemStack = ItemStack.EMPTY

    override fun canCraftInDimensions(width: Int, height: Int): Boolean =
        width * height >= recipeIngredients.size + 1

    override fun matches(input: CraftingInput, level: Level): Boolean {
        val stacks = (0 until input.size()).map { input.getItem(it) }.filter { !it.isEmpty }
        val contracts = stacks.filter(::isRerollableCelestialStack)
        if (contracts.size != 1) return false

        val rest = stacks.filterNot(::isRerollableCelestialStack).toMutableList()
        if (rest.size != recipeIngredients.size) return false
        for (ing in recipeIngredients) {
            val idx = rest.indexOfFirst { ing.test(it) }
            if (idx < 0) return false
            rest.removeAt(idx)
        }
        return rest.isEmpty()
    }

    override fun assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack {
        val contract = (0 until input.size())
            .map { input.getItem(it) }
            .firstOrNull(::isRerollableCelestialStack) ?: return ItemStack.EMPTY
        val result = contract.copy()
        // Stamp the desired direction in CustomData; ContractItem.onCraftedBy applies the reroll
        // when the player actually takes the result. Doing the random reroll in assemble would
        // re-randomise on every grid change, since assemble is called for the result preview too.
        val existing = result.get(DataComponents.CUSTOM_DATA) ?: CustomData.of(CompoundTag())
        val merged = existing.copyTag().also { it.putString(REROLL_PENDING_KEY, direction.name) }
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(merged))
        return result
    }

    companion object {
        const val REROLL_PENDING_KEY = "${WingsContractsMod.MOD_ID}:rerollPending"
    }

    class Serializer : RecipeSerializer<ContractRerollRecipe> {
        override fun codec(): MapCodec<ContractRerollRecipe> = CODEC
        override fun streamCodec(): StreamCodec<RegistryFriendlyByteBuf, ContractRerollRecipe> = STREAM_CODEC

        companion object {
            private val DIRECTION_CODEC: Codec<RerollDirection> = Codec.STRING.xmap(
                { RerollDirection.valueOf(it.uppercase()) },
                { it.name.lowercase() }
            )

            private val CODEC: MapCodec<ContractRerollRecipe> = RecordCodecBuilder.mapCodec { instance ->
                instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter { it.group },
                    CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter { it.category() },
                    DIRECTION_CODEC.fieldOf("direction").forGetter(ContractRerollRecipe::direction),
                    Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").xmap(
                        { list -> NonNullList.of<Ingredient>(Ingredient.EMPTY, *list.toTypedArray()) },
                        { it.toList() }
                    ).forGetter { it.ingredients },
                ).apply(instance, ::ContractRerollRecipe)
            }

            private val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ContractRerollRecipe> = StreamCodec.of(
                { buf, recipe ->
                    buf.writeUtf(recipe.group)
                    buf.writeEnum(recipe.category())
                    buf.writeEnum(recipe.direction)
                    buf.writeCollection(recipe.ingredients) { b, ing ->
                        Ingredient.CONTENTS_STREAM_CODEC.encode(b as RegistryFriendlyByteBuf, ing)
                    }
                },
                { buf ->
                    val group = buf.readUtf()
                    val category = buf.readEnum(CraftingBookCategory::class.java)
                    val direction = buf.readEnum(RerollDirection::class.java)
                    val ingredients = NonNullList.create<Ingredient>().also { list ->
                        val size = buf.readVarInt()
                        repeat(size) { list.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buf)) }
                    }
                    ContractRerollRecipe(group, category, direction, ingredients)
                }
            )
        }
    }
}
