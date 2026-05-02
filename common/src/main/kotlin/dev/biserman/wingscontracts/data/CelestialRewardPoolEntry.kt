package dev.biserman.wingscontracts.data

import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import dev.biserman.wingscontracts.nbt.Reward
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.world.item.ItemStack

class CelestialRewardPoolEntry(
    private val itemRewardTag: CompoundTag?,
    private val commandsReward: Reward.Commands?,
    val value: Double,
    val weight: Int,
    val name: String?,
    val description: String?,
    private val displayItemTag: CompoundTag?,
    val shape: ShapeConstraints?,
) {
    /**
     * Re-parsed on every access. ItemStacks cache `Holder.Reference`s for things like
     * enchantments, and those references go stale after `/reload` rebuilds data-driven
     * registries — re-parsing from the source tag guarantees the holders always come from
     * the live registry, so saving the reward into a contract's NBT can't fail with a stale
     * "reference resource key X is not valid in current registry set" error.
     */
    val reward: Reward
        get() = commandsReward ?: itemRewardTag?.let { tag ->
            val stack = ItemStack.parseOptional(ContractTagHelper.registryAccess!!, tag)
            stack.count = tag.getInt("Count").coerceAtLeast(1)
            Reward.Defined(stack)
        } ?: Reward.Random(1.0)

    val displayItem: ItemStack?
        get() = displayItemTag?.let { tag ->
            val stack = ItemStack.parseOptional(ContractTagHelper.registryAccess!!, tag)
            if (stack.isEmpty) null else stack.also { it.count = tag.getInt("Count").coerceAtLeast(1) }
        }

    companion object {
        fun fromJson(json: JsonObject): CelestialRewardPoolEntry? {
            val value = json.get("value")?.asDouble ?: return null
            val weight = json.get("weight")?.asInt ?: 1

            val (itemRewardTag, commandsReward) = parseRewardSource(json) ?: run {
                WingsContractsMod.LOGGER.warn("Skipping celestialRewardPool entry with no parseable reward: $json")
                return null
            }

            val displayItemTag = json.get("displayItem")?.let {
                JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, it) as CompoundTag
            }

            val name = json.get("name")?.asString
            val description = json.get("description")?.asString
            val shape = json.get("shape")?.takeIf { it.isJsonObject }?.let { ShapeConstraints.fromJson(it.asJsonObject) }

            return CelestialRewardPoolEntry(
                itemRewardTag, commandsReward, value, weight, name, description, displayItemTag, shape
            )
        }

        /**
         * Returns (itemRewardTag, commandsReward) where exactly one is non-null, or null if
         * neither path is parseable. Validates item-reward tags eagerly (parse must succeed
         * once with the current registry) so a malformed entry is dropped at load time.
         */
        private fun parseRewardSource(json: JsonObject): Pair<CompoundTag?, Reward.Commands?>? {
            // Commands reward: presence of a `commands` array.
            json.get("commands")?.takeIf { it.isJsonArray }?.let { commandsArr ->
                val commands = commandsArr.asJsonArray.map { it.asString }
                val label = json.get("label")?.asString ?: ""
                val cmdValue = json.get("value")?.asDouble ?: 1.0
                return null to Reward.Commands(commands, label, cmdValue)
            }
            // Item reward: { "item": { "id": "...", "Count": ... } }
            val itemObj = json.get("item")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, itemObj) as CompoundTag
            val testStack = ItemStack.parseOptional(ContractTagHelper.registryAccess!!, tag)
            if (testStack.isEmpty) return null
            return tag to null
        }
    }
}

class ShapeConstraints(
    val baseUnitsDemandedMin: Int? = null,
    val baseUnitsDemandedMax: Int? = null,
    val maxLifetimeUnitsMin: Int? = null,
    val maxLifetimeUnitsMax: Int? = null,
    val unlimitedLifetimeUnitsChance: Double? = null,
    val maxLevelMin: Int? = null,
    val maxLevelMax: Int? = null,
    val quantityGrowthFactorMin: Double? = null,
    val quantityGrowthFactorMax: Double? = null,
    val quantityGrowthFactor: Double? = null,
    val cycleDurationOptionsMs: List<Long>? = null,
    val cycleDurationWeights: List<Int>? = null,
    val expiresInMin: Int? = null,
    val expiresInMax: Int? = null,
    val neverExpiresChance: Double? = null,
    val maxRerolls: Int? = null,
) {
    companion object {
        fun fromJson(json: JsonObject): ShapeConstraints {
            fun int(key: String) = json.get(key)?.asInt
            fun dbl(key: String) = json.get(key)?.asDouble
            fun longArr(key: String) = json.get(key)?.asJsonArray?.map { it.asLong }
            fun intArr(key: String) = json.get(key)?.asJsonArray?.map { it.asInt }

            // For lifetime-units min/max we accept the unprefixed form ("lifetimeUnitsMin")
            // as the preferred spelling and the prefixed form ("maxLifetimeUnitsMin") as an alias
            // matching the Contract field name. For baseUnitsDemanded a single int can pin both
            // ends, mirroring how the legacy `quantityGrowthFactor` shorthand pins the growth factor.
            val baseUnitsPinned = int("baseUnitsDemanded")
            return ShapeConstraints(
                baseUnitsDemandedMin = int("baseUnitsDemandedMin") ?: baseUnitsPinned,
                baseUnitsDemandedMax = int("baseUnitsDemandedMax") ?: baseUnitsPinned,
                maxLifetimeUnitsMin = int("lifetimeUnitsMin") ?: int("maxLifetimeUnitsMin"),
                maxLifetimeUnitsMax = int("lifetimeUnitsMax") ?: int("maxLifetimeUnitsMax"),
                unlimitedLifetimeUnitsChance = dbl("unlimitedLifetimeUnitsChance"),
                maxLevelMin = int("levelMin") ?: int("maxLevelMin"),
                maxLevelMax = int("levelMax") ?: int("maxLevelMax"),
                quantityGrowthFactorMin = dbl("quantityGrowthFactorMin"),
                quantityGrowthFactorMax = dbl("quantityGrowthFactorMax"),
                quantityGrowthFactor = dbl("quantityGrowthFactor"),
                cycleDurationOptionsMs = longArr("cycleDurationOptionsMs"),
                cycleDurationWeights = intArr("cycleDurationWeights"),
                expiresInMin = int("expiresInMin"),
                expiresInMax = int("expiresInMax"),
                neverExpiresChance = dbl("neverExpiresChance"),
                maxRerolls = int("maxRerolls"),
            )
        }
    }
}
