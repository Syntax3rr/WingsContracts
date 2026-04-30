package dev.biserman.wingscontracts.client.ponder

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.client.ponder.scenes.*
import dev.biserman.wingscontracts.command.LoadContractCommand
import dev.biserman.wingscontracts.registry.ModBlockRegistry
import dev.biserman.wingscontracts.registry.ModItemRegistry
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import java.util.*

object ModPonderPlugin : PonderPlugin {
    val CONTRACT_CATEGORY = WingsContractsMod.prefix("contracts")

    fun getExampleContract(level: Level) = LoadContractCommand.loadContract(
        "{\"targetItems\":\"minecraft:diamond\",\"countPerUnit\": 8,\"reward\":{\"Count\": 1,\"id\":\"minecraft:emerald\"}}",
        level,
        "abyssal"
    ).createItem()

    val uuid1: UUID = UUID.randomUUID()
    val uuid2: UUID = UUID.randomUUID()

    fun getExampleBoundContract1(level: Level) = LoadContractCommand.loadContract(
        "{\"targetItems\":\"minecraft:diamond\",\"countPerUnit\": 1, \"id\": $uuid1, \"matchingContractId\": $uuid2}",
        level,
        "bound"
    ).createItem()

    fun getExampleBoundContract2(level: Level) = LoadContractCommand.loadContract(
        "{\"targetItems\":\"minecraft:dirt\",\"countPerUnit\": 1, \"id\": $uuid2, \"matchingContractId\": $uuid1}",
        level,
        "bound"
    ).createItem()

    fun getExampleCelestialContract(level: Level) = LoadContractCommand.loadContract(
        "{\"targetItems\":\"minecraft:emerald\"," +
            "\"countPerUnit\": 8," +
            "\"reward\":{\"Count\": 1,\"id\":\"minecraft:diamond\"}," +
            "\"maxLevel\": 3," +
            "\"maxLifetimeUnits\": 10," +
            "\"baseUnitsDemanded\": 4," +
            "\"cycleDurationMs\": 0," +
            "\"expiresIn\": -1," +
            "\"rarity\": 2}",
        level,
        "celestial"
    ).createItem()

    override fun getModId() = WingsContractsMod.MOD_ID

    override fun registerScenes(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        IntroScene.register(helper)
        WoolScene.register(helper)
        RedstoneInputScene.register(helper)
        RedstoneOutputScene.register(helper)
        BoundContractScene.register(helper)
        SpigotScene.register(helper)
        CelestialContractScene.register(helper)
    }

    override fun registerTags(helper: PonderTagRegistrationHelper<ResourceLocation>) {
        helper.registerTag(CONTRACT_CATEGORY)
            .addToIndex()
            .item(ModBlockRegistry.CONTRACT_SPIGOT.get() ?: return, true, false)
            .item(ModBlockRegistry.CONTRACT_PORTAL.get() ?: return, true, false)
            .register()

        helper.addToTag(CONTRACT_CATEGORY)
            .add(ModBlockRegistry.CONTRACT_SPIGOT.id)
            .add(ModBlockRegistry.CONTRACT_PORTAL.id)
            .add(ModItemRegistry.CELESTIAL_CONTRACT.id)

        helper.addToTag(AllCreatePonderTags.DISPLAY_SOURCES)
            .add(ModBlockRegistry.CONTRACT_PORTAL.id)
    }
}