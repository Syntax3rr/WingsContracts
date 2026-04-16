@file:OptIn(ExperimentalStdlibApi::class)

package dev.biserman.wingscontracts.nbt

import dev.architectury.registry.fuel.FuelRegistry
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.util.ComponentHelper.trimBrackets
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemAttributeModifiers
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max

class ItemCondition(val text: String, val match: (ItemStack) -> Boolean) {
    override fun toString() = text
}

object ItemConditionParser {
    fun parseEntries(text: String): List<String> {
        val entries = mutableListOf<String>()
        var inQuotes = false
        var currentEntry = ""
        for (i in 0..<text.length) {
            if (text[i] == '\'') {
                if (i == 0 || text[i - 1] != '\\') {
                    inQuotes = !inQuotes
                }
            }

            if (text[i] == ';') {
                if (!inQuotes) {
                    entries.add(currentEntry)
                    currentEntry = ""
                    continue
                }
            }

            currentEntry += text[i]
        }

        if (currentEntry.isNotEmpty()) {
            entries.add(currentEntry)
        }

        return entries.toList()
    }

    val additionalConditions = mutableMapOf<String, (ItemStack) -> String>()

    val navigationRegex = Regex("""(?!\\)\.""")

    // navigate recursively down a path of CompoundTags and convert the end value into a String
    private fun navigate(
        keyComponents: List<String>,
        default: String,
        soFar: (CompoundTag?) -> CompoundTag? = { it }
    ): (CompoundTag?) -> String {
        if (keyComponents.size == 1) {
            return { soFar(it)?.get(keyComponents[0])?.toString() ?: default }
        }

        return navigate(keyComponents.drop(1), default) { it?.getCompound(keyComponents[0]) }
    }

    private fun (ItemStack).getAttributeValue(
        attribute: Attribute,
        operation: AttributeModifier.Operation,
        default: Boolean
    ): Double {
        @Suppress("DEPRECATION")
        val modifiers = if (default) {
            this.item.defaultAttributeModifiers
        } else {
            this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
        }

        val relevantModifiers = modifiers
            .modifiers
            .filter { it.attribute.value() == attribute }
            .filter { it.modifier.operation == operation }
            .map { it.modifier.amount }

        return if (operation == AttributeModifier.Operation.ADD_VALUE) {
            relevantModifiers.sum()
        } else {
            relevantModifiers.reduce(Double::times)
        }
    }

    val conditionRegex = Regex("^(.+?)(!<-|==|!=|<=|>=|<-|!\\$|<|>|\\$)(.+)$")
    fun parseCondition(condition: String): ItemCondition? {
        val match = conditionRegex.matchEntire(condition)
        if (match == null) {
            WingsContractsMod.LOGGER.warn("Failed to parse condition $condition")
            return null
        }
        val key = match.groups[1]?.value?.trim() ?: return null
        val operator = match.groups[2]?.value ?: return null
        val value = match.groups[3]?.value?.trim() ?: return null

        val keyComponents = key.split(navigationRegex)
        val navigationComponents = keyComponents.drop(1)

        fun wrapNavigate(
            default: String
        ): (ItemStack) -> String =
            ({ navigate(navigationComponents, default)(it.get(DataComponents.CUSTOM_DATA)?.tag) })

        val fetchItemValue2: (ItemStack) -> String = when (keyComponents[0]) {
            "tag" -> when {
                value == "true" || value == "false" -> wrapNavigate("false")
                value.toIntOrNull() != null -> wrapNavigate("0")
                value.toDoubleOrNull() != null -> wrapNavigate("0.0")
                else -> wrapNavigate("")
            }

            "defaultAttribute" -> attribute@ ({
                it.getAttributeValue(
                    BuiltInRegistries.ATTRIBUTE.get(
                        ResourceLocation.tryParse(
                            keyComponents.subList(
                                1,
                                keyComponents.size - 1
                            ).joinToString(".")
                        )
                    ) ?: return@attribute "0",
                    AttributeModifier.Operation.entries
                        .firstOrNull { operation -> operation.serializedName == keyComponents[keyComponents.size - 1] }
                        ?: return@attribute "0",
                    default = true
                ).toString()
            })

            "attribute" -> attribute@ ({
                it.components.get(DataComponents.ATTRIBUTE_MODIFIERS)?.modifiers?.forEach { modifier ->
                    WingsContractsMod.LOGGER.info("${modifier.attribute.registeredName}, ${modifier.modifier.operation}, ${modifier.modifier.amount}, ${modifier.modifier().id}")
                }
                it.getAttributeValue(
                    BuiltInRegistries.ATTRIBUTE.get(
                        ResourceLocation.tryParse(
                            keyComponents.subList(
                                1,
                                keyComponents.size - 1
                            ).joinToString(".")
                        )
                    ) ?: return@attribute "0",
                    AttributeModifier.Operation.entries
                        .firstOrNull { operation -> operation.serializedName == keyComponents[keyComponents.size - 1] }
                        ?: return@attribute "0",
                    default = false
                ).toString()
            })

            "mod" -> ({ it.item.`arch$registryName`()?.namespace ?: "" })
            "path" -> ({ it.item.`arch$registryName`()?.path ?: "" })
            "id" -> ({ it.item.`arch$registryName`()?.toString() ?: "" })
            "isBarVisible" -> ({ it.isBarVisible.toString() })
            "barWidth" -> ({ it.barWidth.toString() })
            "barColor" -> ({ it.barColor.toString(16) })
            "hasFoil" -> ({ it.hasFoil().toString() })
            "rarity" -> ({ it.rarity.ordinal.toString() })
            "isDamageable" -> ({ it.isDamageableItem.toString() })
            "durabilityPercent" -> ({ (it.damageValue.toDouble() / max(1.0, it.maxDamage.toDouble())).toString() })
            "isEnchantable" -> ({ it.isEnchantable.toString() })
            "isEnchanted" -> ({ it.isEnchanted.toString() })
            "isFireResistant" -> ({ (it.components.get(DataComponents.FIRE_RESISTANT) != null).toString() })
            "isEdible" -> ({ (it.components.get(DataComponents.FOOD) != null).toString() })
            "maxStackSize" -> ({
                (it.components.get(DataComponents.MAX_STACK_SIZE) ?: Item.DEFAULT_MAX_STACK_SIZE).toString()
            })

            "maxDamage" -> ({ it.get(DataComponents.MAX_DAMAGE)?.toString() ?: "0" })
            "nutrition" -> ({ it.get(DataComponents.FOOD)?.nutrition?.toString() ?: "0" })
            "saturationModifier" -> ({ it.get(DataComponents.FOOD)?.saturation()?.toString() ?: "0.0" })
            "canAlwaysEat" -> ({ it.get(DataComponents.FOOD)?.canAlwaysEat?.toString() ?: "false" })
            "isFastFood" -> ({
                (((it.get(DataComponents.FOOD)?.eatDurationTicks() ?: Int.MAX_VALUE) <= 20).toString())
            })

            "eatSeconds" -> ({ it.get(DataComponents.FOOD)?.eatSeconds?.toString() ?: Int.MAX_VALUE.toString() })
            "isBlock" -> ({ (it.item is BlockItem).toString() })
            "class" -> ({ it.item.javaClass.name })
            "displayName" -> ({ it.displayName.string.trimBrackets() })
            "burnTicks" -> ({ FuelRegistry.get(it).toString() })
            "damage" -> ({ it.get(DataComponents.DAMAGE)?.toString() ?: "0" })
            "isUnbreakable" -> ({ it.get(DataComponents.UNBREAKABLE)?.toString() ?: "false" })
            "customName" -> ({ it.get(DataComponents.CUSTOM_NAME)?.string?.trimBrackets() ?: "" })
            "itemName" -> ({ it.get(DataComponents.ITEM_NAME)?.string?.trimBrackets() ?: "" })
            "lore" -> ({ it.get(DataComponents.LORE)?.toString() ?: "" })
            "repairCost" -> ({ it.get(DataComponents.REPAIR_COST)?.toString() ?: "0" })
            "hasGlintOverride" -> ({ it.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)?.toString() ?: "false" })
            "isTool" -> ({ (it.components.get(DataComponents.TOOL) != null).toString() })
            "miningSpeed" -> ({ it.get(DataComponents.TOOL)?.defaultMiningSpeed?.toString() ?: "0.0" })
            "damagePerBlock" -> ({ it.get(DataComponents.TOOL)?.damagePerBlock?.toString() ?: "0" })
            "enchantments" -> ({
                it.get(DataComponents.ENCHANTMENTS)?.enchantments?.toString() ?: ""
            })

            "storedEnchantments" -> ({ it.get(DataComponents.STORED_ENCHANTMENTS)?.enchantments?.toString() ?: "" })
            "dyedColor" -> ({ it.get(DataComponents.DYED_COLOR)?.rgb?.toString(16) ?: "" })
            "hasArmorTrim" -> ({ (it.components.get(DataComponents.TRIM) != null).toString() })
            "armorTrimPattern" -> ({
                it.get(DataComponents.TRIM)?.pattern()?.value()?.templateItem?.value()?.`arch$registryName`()
                    ?.toString() ?: ""
            })

            "armorTrimMaterial" -> ({
                it.get(DataComponents.TRIM)?.material()?.value()?.ingredient?.value()?.`arch$registryName`()?.toString()
                    ?: ""
            })

            "isJukeboxPlayable" -> ({ (it.components.get(DataComponents.JUKEBOX_PLAYABLE) != null).toString() })
            "jukeboxSong" -> ({ it.get(DataComponents.JUKEBOX_PLAYABLE)?.song?.key?.location()?.toString() ?: "" })
            "noteBlockSound" -> ({ it.get(DataComponents.NOTE_BLOCK_SOUND)?.toString() ?: "false" })
            "baseDyeColor" -> ({ it.get(DataComponents.BASE_COLOR)?.name?.trimBrackets() ?: "" })
            "suspiciousStewEffects" -> ({
                it.get(DataComponents.SUSPICIOUS_STEW_EFFECTS)?.effects?.joinToString(",") {
                    it.effect().unwrapKey().getOrNull().toString()
                } ?: ""
            })

            "potionEffects" -> ({
                it.get(DataComponents.POTION_CONTENTS)?.allEffects?.joinToString(",") {
                    it.effect.unwrapKey().getOrNull().toString()
                } ?: ""
            })

            "potionColor" -> ({ it.get(DataComponents.POTION_CONTENTS)?.color?.toString(16) ?: "" })
            "potionCustomColor" -> ({
                it.get(DataComponents.POTION_CONTENTS)?.customColor?.getOrNull()?.toString(16) ?: ""
            })

            "potionType" -> ({
                it.get(DataComponents.POTION_CONTENTS)?.potion?.get()?.unwrapKey()?.getOrNull()?.location()?.toString()
                    ?: ""
            })
            else -> additionalConditions[keyComponents[0]] ?: throw Error("Condition key not recognized: ${keyComponents[0]}")
        }

        val fetchItemValue: (ItemStack) -> String = {
            val itemValue = fetchItemValue2(it)
            WingsContractsMod.LOGGER.info("Condition $condition evaluated to $itemValue")
            itemValue
        }

        val compareToValue: String.() -> Int = when {
            value == "true" || value == "false" -> ({ this.compareTo(value) })
            value.toDoubleOrNull() != null -> ({ this.toDouble().compareTo(value.toDouble()) })
            else -> ({ this.trim('"').compareTo(value.trim('"', '\'')) })
        }

        return when (operator) {
            "==" -> ItemCondition(condition) { fetchItemValue(it).compareToValue() == 0 }
            "!=" -> ItemCondition(condition) { fetchItemValue(it).compareToValue() != 0 }
            "<" -> ItemCondition(condition) { fetchItemValue(it).compareToValue() < 0 }
            ">" -> ItemCondition(condition) { fetchItemValue(it).compareToValue() > 0 }
            "<=" -> ItemCondition(condition) { fetchItemValue(it).compareToValue() <= 0 }
            ">=" -> ItemCondition(condition) { fetchItemValue(it).compareToValue() >= 0 }
            "<-" -> ItemCondition(condition) { fetchItemValue(it).contains(value) }
            "!<-" -> ItemCondition(condition) { !fetchItemValue(it).contains(value) }
            "$" -> {
                val regex = Regex(value)
                ItemCondition(condition) { regex.matches(fetchItemValue(it)) }
            }

            "!$" -> {
                val regex = Regex(value)
                ItemCondition(condition) { !regex.matches(fetchItemValue(it)) }
            }

            else -> {
                WingsContractsMod.LOGGER.warn("Failed to parse condition $condition: unknown operator $operator")
                null
            }
        }
    }

    fun parse(text: String): List<ItemCondition> {
        val entries = parseEntries(text)
        return entries.mapNotNull { parseCondition(it) }
    }
}