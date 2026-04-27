package dev.biserman.wingscontracts.compat.computercraft

import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.block.ContractPortalBlockEntity
import dev.biserman.wingscontracts.compat.computercraft.DetailsHelper.details
import dev.biserman.wingscontracts.core.ServerContract
import dev.biserman.wingscontracts.data.LoadedContracts

class ContractPortalPeripheral(private val portal: ContractPortalBlockEntity) : IPeripheral {
    @Suppress("CovariantEquals")
    override fun equals(other: IPeripheral?) = other == this
    override fun getType() = "${WingsContractsMod.MOD_ID}.portal"

    val contract get() = LoadedContracts[portal.contractSlot]

    @LuaFunction
    fun getContractDetails(): Map<String, Any> = if (portal.contractSlot.isEmpty) {
        mapOf()
    } else {
        portal.contractSlot.details
    }

    @LuaFunction
    fun getCompletion() = {
        val serverContract = contract as? ServerContract
        serverContract?.let { it.unitsFulfilled.toDouble() / it.unitsDemanded } ?: -1
    }

    @LuaFunction
    fun getCachedRewardsDetails(): List<Map<String, Any>> = portal.cachedRewards.items.filter { !it.isEmpty }.map { it.details }

    @LuaFunction
    fun getCachedInputDetails(): List<Map<String, Any>> = portal.cachedInput.items.filter { !it.isEmpty }.map { it.details }
}