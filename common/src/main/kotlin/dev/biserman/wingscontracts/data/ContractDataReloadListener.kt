package dev.biserman.wingscontracts.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import dev.architectury.platform.Platform
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.core.AbyssalContract
import dev.biserman.wingscontracts.core.CelestialContract
import dev.biserman.wingscontracts.core.Contract.Companion.countPerUnit
import dev.biserman.wingscontracts.core.Contract.Companion.name
import dev.biserman.wingscontracts.core.Contract.Companion.requiresAll
import dev.biserman.wingscontracts.core.Contract.Companion.requiresAny
import dev.biserman.wingscontracts.core.Contract.Companion.requiresNot
import dev.biserman.wingscontracts.core.Contract.Companion.targetBlockTagKeys
import dev.biserman.wingscontracts.core.Contract.Companion.targetBlockTags
import dev.biserman.wingscontracts.core.Contract.Companion.targetConditionsKeys
import dev.biserman.wingscontracts.core.Contract.Companion.targetItemKeys
import dev.biserman.wingscontracts.core.Contract.Companion.targetItems
import dev.biserman.wingscontracts.core.Contract.Companion.targetTagKeys
import dev.biserman.wingscontracts.core.Contract.Companion.targetTags
import dev.biserman.wingscontracts.data.ContractSavedData.Companion.random
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.item.ItemStack
import kotlin.math.pow

val GSON: Gson = (GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create()

class RewardBagEntry(val item: ItemStack, val value: Double, val weight: Int, val formatString: String? = null)

object ContractDataReloadListener : SimpleJsonResourceReloadListener(GSON, "wingscontracts") {
    var dataVersion = 0
    var data = ContractResourceData()
        get() {
            return if (dataVersion == field.version) {
                field
            } else {
                field = resolveContracts(dataVersion)
                field
            }
        }

    override fun prepare(
        resourceManager: ResourceManager,
        profilerFiller: ProfilerFiller
    ): Map<ResourceLocation, JsonElement> {
        return super.prepare(resourceManager, profilerFiller)
    }

    fun randomTag(): ContractTag {
        if (data.availableContracts.isEmpty()) {
            WingsContractsMod.LOGGER.warn("Available contracts pool is empty, returning unknown contract.")
            val contract = ContractTag(CompoundTag())
            contract.name = Component.translatable("${WingsContractsMod.MOD_ID}.contract.unknown").string
            return contract
        } else {
            return ContractTag(data.availableContracts.random().tag.copy())
        }
    }

    fun randomCelestialRewardPoolEntry(): CelestialRewardPoolEntry? {
        val pool = data.availableCelestialRewardPool
        if (pool.isEmpty()) return null
        val totalWeight = pool.sumOf { it.weight }
        if (totalWeight <= 0) return null
        var pick = random.nextInt(totalWeight)
        for (entry in pool) {
            pick -= entry.weight
            if (pick < 0) return entry
        }
        return pool.last()
    }

    fun getCelestialContractByName(targetName: String): ContractTag? {
        return data.availableCelestialContracts.firstOrNull { it.name == targetName }?.let {
            ContractTag(it.tag.copy())
        }
    }

    fun getCelestialRewardPoolEntryByName(targetName: String): CelestialRewardPoolEntry? =
        data.availableCelestialRewardPool.firstOrNull { it.name == targetName }

    var jsonBlobs = mutableMapOf<ResourceLocation, JsonElement>()

    override fun apply(
        jsonMap: Map<ResourceLocation, JsonElement>,
        resourceManager: ResourceManager,
        profilerFiller: ProfilerFiller
    ) {
        jsonBlobs = jsonMap.toMutableMap()
        dataVersion += 1
    }

    fun resolveContracts(version: Int): ContractResourceData {
        WingsContractsMod.LOGGER.info("Building abyssal contracts pool...")
        var skippedBecauseUnloaded = 0

        val allDefaultContracts = mutableListOf<ContractTag>()
        val nonDefaultDefaultContracts = mutableListOf<ContractTag>()
        val allDefaultRewards = mutableListOf<RewardBagEntry>()
        val nonDefaultDefaultRewards = mutableListOf<RewardBagEntry>()
        val fullRewardBlocklist = mutableListOf<String>()
        val nonDefaultRewardBlocklist = mutableListOf<String>()
        val allCelestialContracts = mutableListOf<ContractTag>()
        val nonDefaultCelestialContracts = mutableListOf<ContractTag>()
        val allCelestialRewardPool = mutableListOf<CelestialRewardPoolEntry>()
        val nonDefaultCelestialRewardPool = mutableListOf<CelestialRewardPoolEntry>()

        for ((resourceLocation, json) in jsonBlobs) {
            if (resourceLocation.path.startsWith("_")) {
                continue
            }

            WingsContractsMod.LOGGER.info("...$resourceLocation")

            val isDefault = resourceLocation.path.endsWith("_default")

            try {
                val jsonObject = json.asJsonObject
                val parsedContracts = jsonObject.get("contracts")?.asJsonArray?.map {
                    ContractTag.fromJson(it.asJsonObject)
                } ?: listOf()
                val validationResult = validateContracts(parsedContracts, resourceLocation, isDefault)
                skippedBecauseUnloaded += validationResult.skippedBecauseUnloaded
                allDefaultContracts.addAll(validationResult.allAvailableContracts)
                nonDefaultDefaultContracts.addAll(validationResult.nonDefaultAvailableContracts)

                val parsedDefaultRewards = jsonObject.get("rewards")?.asJsonArray?.mapNotNull {
                    val itemStack = ItemStack.parseOptional(
                        ContractTagHelper.registryAccess!!,
                        JsonOps.INSTANCE.convertTo(
                            NbtOps.INSTANCE,
                            it.asJsonObject.get("item")
                        ) as CompoundTag
                    )

                    if (itemStack == null) {
                        WingsContractsMod.LOGGER.warn("Could not find itemStack ${it.asJsonObject.get("item")}")
                        return@mapNotNull null
                    }

                    RewardBagEntry(
                        itemStack,
                        it.asJsonObject.get("value").asDouble,
                        it.asJsonObject.get("weight").asInt,
                        if (it.asJsonObject.has("formatString")) {
                            it.asJsonObject.get("formatString").asString
                        } else {
                            null
                        }
                    )
                } ?: listOf()

                allDefaultRewards.addAll(parsedDefaultRewards)
                if (!isDefault) {
                    nonDefaultDefaultRewards.addAll(parsedDefaultRewards)
                }

                val parsedRewardBlocklist = jsonObject.get("blockedReplacementRewards")
                    ?.asJsonArray?.map { it.asString } ?: listOf()
                fullRewardBlocklist.addAll(parsedRewardBlocklist)
                if (!isDefault) {
                    nonDefaultRewardBlocklist.addAll(parsedRewardBlocklist)
                }

                val parsedCelestialContracts = jsonObject.get("celestialContracts")?.asJsonArray?.map {
                    ContractTag.fromJson(it.asJsonObject)
                } ?: listOf()
                val celestialValidation =
                    validateCelestialContracts(parsedCelestialContracts, resourceLocation, isDefault)
                skippedBecauseUnloaded += celestialValidation.skippedBecauseUnloaded
                allCelestialContracts.addAll(celestialValidation.allAvailableContracts)
                nonDefaultCelestialContracts.addAll(celestialValidation.nonDefaultAvailableContracts)

                val parsedCelestialPool = jsonObject.get("celestialRewardPool")?.asJsonArray?.mapNotNull {
                    val entry = CelestialRewardPoolEntry.fromJson(it.asJsonObject)
                    when {
                        entry == null -> null
                        entry.value <= 0.0 -> {
                            WingsContractsMod.LOGGER.warn(
                                "Skipping celestialRewardPool entry with non-positive value (${entry.value}) at $resourceLocation"
                            )
                            null
                        }
                        entry.weight <= 0 -> {
                            WingsContractsMod.LOGGER.warn(
                                "Skipping celestialRewardPool entry with non-positive weight (${entry.weight}) at $resourceLocation"
                            )
                            null
                        }
                        else -> entry
                    }
                } ?: listOf()
                allCelestialRewardPool.addAll(parsedCelestialPool)
                if (!isDefault) {
                    nonDefaultCelestialRewardPool.addAll(parsedCelestialPool)
                }
            } catch (e: Exception) {
                WingsContractsMod.LOGGER.error("Error while loading available contracts at $resourceLocation", e)
            }
        }

        if (skippedBecauseUnloaded != 0) {
            WingsContractsMod.LOGGER.info("Skipped $skippedBecauseUnloaded contracts from unloaded mods.")
        }

        return ContractResourceData(
            allDefaultContracts,
            nonDefaultDefaultContracts,
            allDefaultRewards,
            nonDefaultDefaultRewards,
            fullRewardBlocklist,
            nonDefaultRewardBlocklist,
            allCelestialContracts,
            nonDefaultCelestialContracts,
            allCelestialRewardPool,
            nonDefaultCelestialRewardPool,
            version
        )
    }

    data class ValidatedContractsResult(
        val allAvailableContracts: List<ContractTag>,
        val nonDefaultAvailableContracts: List<ContractTag>,
        val skippedBecauseUnloaded: Int,
    )

    fun validateContracts(
        parsedContracts: List<ContractTag>,
        resourceLocation: ResourceLocation,
        isDefault: Boolean
    ): ValidatedContractsResult {
        val allAvailableContracts = mutableListOf<ContractTag>()
        val nonDefaultAvailableContracts = mutableListOf<ContractTag>()

        var skippedBecauseUnloaded = 0
        for (contract in parsedContracts) {
            // skip contracts that only apply to unloaded mods
            val targetItemKeys = contract.targetItemKeys ?: listOf()
            val targetTagKeys = contract.targetTagKeys ?: listOf()
            val targetBlockTagKeys = contract.targetBlockTagKeys ?: listOf()

            val allItemsFailedLoad = targetItemKeys
                .all { it.contains(':') && !Platform.isModLoaded(it.split(":")[0]) }
            val allBlockTagsFailedLoad = targetBlockTagKeys
                .all { it.contains(':') && !Platform.isModLoaded(it.split(":")[0].trimStart('#')) }

            val isJustCondition = !contract.targetConditionsKeys.isNullOrBlank()
                    && targetItemKeys.isEmpty()
                    && targetTagKeys.isEmpty()
                    && targetBlockTagKeys.isEmpty()

            val allFailedLoad = allItemsFailedLoad
                    && allBlockTagsFailedLoad
                    && !isJustCondition
                    && targetTagKeys.isEmpty()

            // check for required mods
            val allRequiredModsLoaded = contract.requiresAll.isNullOrBlank()
                    || contract.requiresAll!!.split(',').all { Platform.isModLoaded(it) }
            val anyRequiredModsLoaded = contract.requiresAny.isNullOrBlank()
                    || contract.requiresAny!!.split(',').any { Platform.isModLoaded(it) }

            if (allFailedLoad || !allRequiredModsLoaded || !anyRequiredModsLoaded) {
                if (!isDefault) {
                    WingsContractsMod.LOGGER.warn("Skipped custom contract $contract because required mod was not found.")
                }
                skippedBecauseUnloaded++
                continue
            }

            val blockedModFound = !contract.requiresNot.isNullOrBlank()
                    && contract.requiresNot!!.split(',').any { Platform.isModLoaded(it) }
            if (blockedModFound) {
                if (!isDefault) {
                    WingsContractsMod.LOGGER.warn("Skipped custom contract $contract because blocked mod was found.")
                }
                continue
            }

            if (AbyssalContract.load(contract).isValid) {
                allAvailableContracts.add(contract)
                if (!isDefault) {
                    nonDefaultAvailableContracts.add(contract)
                }
            } else {
                WingsContractsMod.LOGGER.warn("Found invalid contract $contract in $resourceLocation")
            }
        }

        listOf(allAvailableContracts, nonDefaultAvailableContracts).forEach { contractList ->
            removeEmptyTags(contractList)
            removeImpossibleUnitDemands(contractList)
        }

        return ValidatedContractsResult(
            allAvailableContracts,
            nonDefaultAvailableContracts,
            skippedBecauseUnloaded
        )
    }

    fun validateCelestialContracts(
        parsedContracts: List<ContractTag>,
        resourceLocation: ResourceLocation,
        isDefault: Boolean,
    ): ValidatedContractsResult {
        val allAvailable = mutableListOf<ContractTag>()
        val nonDefaultAvailable = mutableListOf<ContractTag>()
        var skippedBecauseUnloaded = 0

        for (contract in parsedContracts) {
            val targetItemKeys = contract.targetItemKeys ?: listOf()
            val targetTagKeys = contract.targetTagKeys ?: listOf()
            val targetBlockTagKeys = contract.targetBlockTagKeys ?: listOf()

            val allItemsFailedLoad = targetItemKeys
                .all { it.contains(':') && !Platform.isModLoaded(it.split(":")[0]) }
            val allBlockTagsFailedLoad = targetBlockTagKeys
                .all { it.contains(':') && !Platform.isModLoaded(it.split(":")[0].trimStart('#')) }
            val isJustCondition = !contract.targetConditionsKeys.isNullOrBlank()
                && targetItemKeys.isEmpty()
                && targetTagKeys.isEmpty()
                && targetBlockTagKeys.isEmpty()
            // Celestials may also have no targets at all (they fall back to celestialCurrencyAnchor at load).
            val isJustCurrency = targetItemKeys.isEmpty()
                && targetTagKeys.isEmpty()
                && targetBlockTagKeys.isEmpty()
                && contract.targetConditionsKeys.isNullOrBlank()

            val allFailedLoad = allItemsFailedLoad
                && allBlockTagsFailedLoad
                && !isJustCondition
                && !isJustCurrency
                && targetTagKeys.isEmpty()

            val allRequiredModsLoaded = contract.requiresAll.isNullOrBlank()
                || contract.requiresAll!!.split(',').all { Platform.isModLoaded(it) }
            val anyRequiredModsLoaded = contract.requiresAny.isNullOrBlank()
                || contract.requiresAny!!.split(',').any { Platform.isModLoaded(it) }

            if (allFailedLoad || !allRequiredModsLoaded || !anyRequiredModsLoaded) {
                if (!isDefault) {
                    WingsContractsMod.LOGGER.warn(
                        "Skipped custom celestial contract $contract because required mod was not found."
                    )
                }
                skippedBecauseUnloaded++
                continue
            }
            val blockedModFound = !contract.requiresNot.isNullOrBlank()
                && contract.requiresNot!!.split(',').any { Platform.isModLoaded(it) }
            if (blockedModFound) {
                if (!isDefault) {
                    WingsContractsMod.LOGGER.warn(
                        "Skipped custom celestial contract $contract because blocked mod was found."
                    )
                }
                continue
            }

            if ((contract.countPerUnit ?: 0) <= 0) {
                WingsContractsMod.LOGGER.warn(
                    "Celestial contract is missing countPerUnit (or it is non-positive): $contract"
                )
                continue
            }
            val loaded = CelestialContract.load(contract)
            if (!loaded.reward.isValid) {
                WingsContractsMod.LOGGER.warn("Celestial contract has no valid reward: $contract")
                continue
            }

            allAvailable.add(contract)
            if (!isDefault) nonDefaultAvailable.add(contract)
        }

        listOf(allAvailable, nonDefaultAvailable).forEach { removeEmptyTags(it) }

        return ValidatedContractsResult(allAvailable, nonDefaultAvailable, skippedBecauseUnloaded)
    }

    fun removeEmptyTags(contractList: MutableList<ContractTag>) {
        contractList.removeIf { contract ->
            val itemTags = contract.targetTags ?: listOf()
            val blockTags = contract.targetBlockTags ?: listOf()
            val targetItems = contract.targetItems ?: listOf()

            if (itemTags.isEmpty() && blockTags.isEmpty()) {
                return@removeIf false
            }

            if (itemTags.all { BuiltInRegistries.ITEM.getTagOrEmpty(it).count() == 0 }
                && blockTags.all { BuiltInRegistries.BLOCK.getTagOrEmpty(it).count() == 0 }
                && targetItems.isEmpty()) {
                WingsContractsMod.LOGGER.warn("Removing empty tag contract: $contract")
                return@removeIf true
            }

            return@removeIf false
        }
    }

    // remove contracts that would be impossible to fulfill due to the interaction between their target's max stack size and
    // the container size of the Contract Portal
    fun removeImpossibleUnitDemands(contractList: MutableList<ContractTag>) {
        contractList.removeIf { contract ->
            val countPerUnit = contract.countPerUnit ?: 64

            val itemTags = contract.targetTags ?: listOf()
            val blockTags = contract.targetBlockTags ?: listOf()
            val targetItems = contract.targetItems ?: listOf()

            val maxStackSize =
                itemTags.flatMap { BuiltInRegistries.ITEM.getTagOrEmpty(it).map { it.value().defaultMaxStackSize } }
                    .plus(blockTags.flatMap {
                        BuiltInRegistries.BLOCK.getTagOrEmpty(it).map { it.value().asItem().defaultMaxStackSize }
                    })
                    .plus(targetItems.map { it.defaultMaxStackSize })
                    .maxOrNull() ?: 64

            val defaultCountPerUnitMultiplier = ModConfig.SERVER.defaultCountPerUnitMultiplier.get()
            val variance = ModConfig.SERVER.variance.get()
            val portalContainerSize = ModConfig.SERVER.contractPortalInputSlots.get()

            if (countPerUnit * defaultCountPerUnitMultiplier * (1 + variance).pow(2) > portalContainerSize * maxStackSize) {
                WingsContractsMod.LOGGER.warn("Removing contract with countPerUnit too large for contract portal: $contract")
                return@removeIf true
            }

            return@removeIf false
        }
    }
}