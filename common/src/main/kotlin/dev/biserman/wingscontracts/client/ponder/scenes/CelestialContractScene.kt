package dev.biserman.wingscontracts.client.ponder.scenes

import com.simibubi.create.foundation.ponder.CreateSceneBuilder
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.client.ponder.ModPonderPlugin
import dev.biserman.wingscontracts.registry.ModItemRegistry
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3


object CelestialContractScene {
    fun register(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        helper.forComponents(ModItemRegistry.CELESTIAL_CONTRACT.id)
            .addStoryBoard(
                WingsContractsMod.prefix("celestial_contract/intro"),
                ::scene,
                ModPonderPlugin.CONTRACT_CATEGORY
            )
    }

    fun scene(builder: SceneBuilder, util: SceneBuildingUtil) {
        val scene = CreateSceneBuilder(builder)
        scene.title("celestial_contract.intro", "Rerolling Celestial Contracts")
        scene.configureBasePlate(0, 0, 3)
        scene.showBasePlate()
        scene.idle(10)

        val center = util.grid().at(1, 1, 1)
        // Strip the portal that lives at center of the shared intro.nbt.
        scene.world().setBlock(center, Blocks.AIR.defaultBlockState(), false)

        // Bottom of the center cell, where the contract item floats.
        val ground = util.vector().centerOf(center).subtract(0.0, 0.5, 0.0)

        scene.overlay()
            .showText(90)
            .text("text_1")
            .attachKeyFrame()
            .placeNearTarget()

        scene.idle(95)

        var contract = spawnContract(scene, ground)

        scene.idle(20)

        scene.overlay()
            .showText(90)
            .text("text_2")
            .attachKeyFrame()
            .pointAt(ground)
            .placeNearTarget()

        scene.idle(95)

        val bolt = scene.world().createEntity { level ->
            val entity = EntityType.LIGHTNING_BOLT.create(level) as LightningBolt
            entity.setPos(ground.x, ground.y, ground.z)
            // visualOnly suppresses damage/fire side effects so the contract item survives the strike.
            entity.setVisualOnly(true)
            entity
        }
        scene.idle(20)
        scene.world().modifyEntity(bolt, Entity::discard)

        scene.idle(10)

        scene.world().modifyEntity(contract, Entity::discard)
        // showIndependentSection registers center as a WorldSectionElement so that setBlock is
        // included in a render pass. Without this, setBlock writes to the level but the position
        // is never in any section's selection and the block is invisible.
        scene.world().showIndependentSection(util.select().position(center), Direction.UP)
        scene.world().setBlock(center, Blocks.LAVA.defaultBlockState(), false)
        contract = spawnContract(scene, ground)

        scene.idle(15)

        scene.overlay()
            .showText(90)
            .text("text_3")
            .attachKeyFrame()
            .pointAt(util.vector().centerOf(center))
            .placeNearTarget()

        scene.idle(95)

        scene.world().setBlock(center, Blocks.AIR.defaultBlockState(), false)
        scene.world().modifyEntity(contract, Entity::discard)

        scene.idle(10)

        scene.overlay()
            .showText(90)
            .text("text_4")
            .attachKeyFrame()
            .placeNearTarget()

        scene.idle(95)
    }

    private fun spawnContract(scene: CreateSceneBuilder, at: Vec3) =
        scene.world().createItemEntity(at, Vec3.ZERO, ModPonderPlugin.getExampleCelestialContract(scene.scene.world))
            .also { link ->
                // The base plate's y=0 blocks aren't reliable as a collision floor in the ponder
                // simulator, so disable gravity instead of trusting it to land the item correctly.
                scene.world().modifyEntity(link) { entity ->
                    if (entity is ItemEntity) entity.isNoGravity = true
                }
            }
}
