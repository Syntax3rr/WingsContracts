package dev.biserman.wingscontracts.advancements

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import dev.biserman.wingscontracts.advancements.ContractCompleteTrigger.TriggerInstance
import dev.biserman.wingscontracts.nbt.ItemConditionParser
import net.minecraft.advancements.critereon.ContextAwarePredicate
import net.minecraft.advancements.critereon.EntityPredicate
import net.minecraft.advancements.critereon.SimpleCriterionTrigger
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.ItemStack
import java.util.*
import java.util.function.Predicate

class ContractCompleteTrigger : SimpleCriterionTrigger<TriggerInstance>() {
    fun trigger(player: ServerPlayer, stack: ItemStack) {
        trigger(player, Predicate { instance: TriggerInstance -> instance.matches(stack) })
    }

    override fun codec(): Codec<TriggerInstance> = CODEC

    class TriggerInstance(
        val player: Optional<ContextAwarePredicate>, val conditionString: Optional<String>
    ) : SimpleInstance {
        val conditions by lazy { ItemConditionParser.parse(conditionString.get()) }

        fun matches(stack: ItemStack): Boolean {
            return conditions.all { it.match(stack) }
        }

        override fun player() = player
    }

    companion object {
        val INSTANCE = ContractCompleteTrigger()
        val CODEC: Codec<TriggerInstance> = RecordCodecBuilder.create {
            it.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
                    .forGetter { it.player },
                StringRepresentable.StringRepresentableCodec.STRING.optionalFieldOf("item_matches")
                    .forGetter { it.conditionString })
                .apply(it, ::TriggerInstance)
        }
    }
}