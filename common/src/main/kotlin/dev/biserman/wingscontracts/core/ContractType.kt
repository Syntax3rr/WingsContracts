package dev.biserman.wingscontracts.core

import dev.biserman.wingscontracts.data.ContractSavedData
import dev.biserman.wingscontracts.nbt.ContractTag

enum class ContractType(val id: Int, val load: (ContractTag, ContractSavedData?) -> Contract) {
    ABYSSAL(1, AbyssalContract::load),
    BOUND(2, BoundContract::load);

    companion object {
        private val BY_ID = entries.associateBy { it.id }
        fun fromId(id: Int): ContractType? = BY_ID[id]
    }
}
