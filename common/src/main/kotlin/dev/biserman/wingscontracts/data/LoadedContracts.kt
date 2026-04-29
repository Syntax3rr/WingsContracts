package dev.biserman.wingscontracts.data

import dev.biserman.wingscontracts.core.Contract
import dev.biserman.wingscontracts.core.Contract.Companion.id
import dev.biserman.wingscontracts.core.Contract.Companion.type
import dev.biserman.wingscontracts.nbt.ContractTag
import dev.biserman.wingscontracts.nbt.ContractTagHelper
import net.minecraft.world.item.ItemStack
import java.util.*

object LoadedContracts {
    private val contracts: MutableMap<UUID, Contract> = mutableMapOf()

    operator fun get(id: UUID) = contracts[id]
    operator fun get(contractItemStack: ItemStack): Contract? {
        val contractTag = ContractTagHelper.getContractTag(contractItemStack) ?: return null
        return get(contractTag)
    }

    operator fun get(contractTag: ContractTag): Contract? {
        val id = contractTag.id ?: return null
        if (contracts.containsKey(id)) return contracts[id]

        val contract = (contractTag.type ?: return null).load(contractTag, null)
        contracts[id] = contract
        return contract
    }

    fun invalidate(id: UUID) {
        contracts.remove(id)
    }

    fun update(contract: Contract) {
        contracts[contract.id] = contract
    }

    fun clear() = contracts.clear()
}
