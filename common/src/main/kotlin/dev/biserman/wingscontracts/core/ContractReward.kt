package dev.biserman.wingscontracts.core

import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.core.Contract.Companion.translateContract
import dev.biserman.wingscontracts.data.ContractDataReloadListener
import dev.biserman.wingscontracts.nbt.Reward
import dev.biserman.wingscontracts.util.ComponentHelper.trimBrackets
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.item.Items as VanillaItems
import kotlin.jvm.optionals.getOrNull
import kotlin.math.floor

data class RewardContext(
    val level: ServerLevel,
    val executor: ServerPlayer?,
    val pos: Vec3,
)

data class RewardOutcome(val items: List<ItemStack>, val scoreboardValue: Double)

data class ConsumeResult(
    val items: List<ItemStack>,
    val unitsConsumed: Int,
    val scoreboardValue: Double,
) {
    companion object {
        val NONE = ConsumeResult(emptyList(), 0, 0.0)
    }
}

sealed class ContractReward {
    abstract val rewardPerUnit: Int
    abstract val isValid: Boolean
    abstract fun formatReward(count: Int): String
    abstract fun apply(units: Int, ctx: RewardContext): RewardOutcome
    abstract fun toTagReward(): Reward

    data class Items(val stack: ItemStack) : ContractReward() {
        override val rewardPerUnit get() = stack.count
        override val isValid get() = stack.item != VanillaItems.AIR
        override fun toTagReward(): Reward = Reward.Defined(stack)

        override fun formatReward(count: Int): String {
            val rewardEntry =
                ContractDataReloadListener.data.defaultRewards.firstOrNull { it.item.item == stack.item }
            if (rewardEntry?.formatString != null) {
                return String.format(rewardEntry.formatString, count)
            }

            val trimmed = stack.displayName.string.trimBrackets()
            return when {
                stack.has(DataComponents.STORED_ENCHANTMENTS) -> {
                    val enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS)?.entrySet()?.mapNotNull { kvp ->
                        val resourceLocation = kvp.key.unwrapKey().getOrNull()?.location() ?: return@mapNotNull null
                        val enchantmentLevel = kvp.intValue
                        val name = Component.translatable(
                            "enchantment.${resourceLocation.namespace}.${resourceLocation.path}"
                        ).string
                        val levelString = Component.translatable("enchantment.level.$enchantmentLevel").string
                        "$name $levelString"
                    } ?: listOf()

                    translateContract(
                        "enchanted_book_format",
                        count,
                        enchantments.joinToString(" + "),
                        trimmed
                    ).string
                }

                stack.isEnchanted -> translateContract("enchanted_reward_format", count, trimmed).string
                else -> "$count $trimmed"
            }
        }

        override fun apply(units: Int, ctx: RewardContext): RewardOutcome {
            val totalCount = stack.count * units
            val fullStackCount = Mth.floor(totalCount.toDouble() / stack.maxStackSize)
            val remainder = totalCount % stack.maxStackSize
            val fullStacks = (1..fullStackCount).map { stack.copyWithCount(stack.maxStackSize) }
            val items = if (remainder == 0) fullStacks else fullStacks + stack.copyWithCount(remainder)
            val score = items.sumOf { floor(ContractDataReloadListener.data.valueReward(it)) }
            return RewardOutcome(items, score)
        }
    }

    data class Commands(
        val commands: List<String>,
        val label: String,
        val value: Double
    ) : ContractReward() {
        override val rewardPerUnit get() = 1
        override val isValid get() = commands.isNotEmpty()
        override fun toTagReward(): Reward = Reward.Commands(commands, label, value)

        override fun formatReward(count: Int): String {
            // Label is a translation key, falling back to the literal string if not registered.
            val translated = Component.translatable(label).string
            return if (count <= 1) translated else "$translated ×$count"
        }

        override fun apply(units: Int, ctx: RewardContext): RewardOutcome {
            val source = CommandSourceStack(
                CommandSource.NULL,
                ctx.pos,
                Vec2.ZERO,
                ctx.level,
                2,
                ctx.executor?.name?.string ?: "WingsContracts",
                ctx.executor?.displayName ?: Component.literal("WingsContracts"),
                ctx.level.server,
                ctx.executor,
            )
            repeat(units) {
                commands.forEach { command ->
                    try {
                        ctx.level.server.commands.performPrefixedCommand(source, command)
                    } catch (t: Throwable) {
                        WingsContractsMod.LOGGER.warn("Contract reward command '$command' failed", t)
                    }
                }
            }
            return RewardOutcome(emptyList(), floor(value) * units)
        }
    }
}
