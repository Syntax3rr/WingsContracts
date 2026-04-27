package dev.biserman.wingscontracts.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.core.ContractType
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture
import java.util.function.Function

class ContractTypeArgument : ArgumentType<String> {
    override fun parse(stringReader: StringReader): String {
        val string = stringReader.readUnquotedString()
        if (string !in options) {
            throw ERROR_INVALID.createWithContext(stringReader, string)
        } else {
            return string
        }
    }

    override fun <S> listSuggestions(
        commandContext: CommandContext<S?>,
        suggestionsBuilder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        return if (commandContext.getSource() is SharedSuggestionProvider) {
            SharedSuggestionProvider.suggest(options.keys, suggestionsBuilder)
        } else Suggestions.empty()
    }

    override fun getExamples() = options.keys

    companion object {
        val options = ContractType.entries.associateBy { it.name.lowercase() }

        private val ERROR_INVALID: DynamicCommandExceptionType = DynamicCommandExceptionType(Function {
            Component.translatable(
                "commands.${WingsContractsMod.MOD_ID}.argument.contract_type.invalid",
                *arrayOf(it)
            )
        })

        fun contractType() = ContractTypeArgument()
    }
}
