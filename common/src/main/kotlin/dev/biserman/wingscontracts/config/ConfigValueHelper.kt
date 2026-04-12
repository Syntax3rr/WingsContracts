package dev.biserman.wingscontracts.config

import dev.biserman.wingscontracts.WingsContractsMod
import net.neoforged.neoforge.common.ModConfigSpec

fun <T> (ModConfigSpec.ConfigValue<T>).getOrDefault(): T {
    try {
        return this.getRaw()
    } catch (e: Exception) {
        val default = this.getDefault()
        WingsContractsMod.LOGGER.info("Server config not yet loaded. Using default value: $default")
        return default
    }
}