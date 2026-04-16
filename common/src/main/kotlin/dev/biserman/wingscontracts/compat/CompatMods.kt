package dev.biserman.wingscontracts.compat

import dev.architectury.platform.Platform
import dev.biserman.wingscontracts.compat.computercraft.ComputercraftItemDetailProvider

object CompatMods {
    const val COMPUTERCRAFT = "computercraft"
    const val CREATE = "create"

    fun init() {
        if (Platform.isModLoaded(COMPUTERCRAFT)) {
            ComputercraftItemDetailProvider.register()
        }
    }
}