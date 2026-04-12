package dev.biserman.wingscontracts.registry

import dev.architectury.registry.registries.DeferredRegister
import dev.architectury.registry.registries.RegistrySupplier
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.advancements.ContractCompleteTrigger
import net.minecraft.advancements.CriterionTrigger
import net.minecraft.core.registries.Registries

@Suppress("MemberVisibilityCanBePrivate")
object ModCriteriaTriggerRegistry {
    val CRITERIA_TRIGGERS: DeferredRegister<CriterionTrigger<*>> = DeferredRegister
        .create(WingsContractsMod.MOD_ID, Registries.TRIGGER_TYPE)

    val CONTRACT_COMPLETE_TRIGGER: RegistrySupplier<CriterionTrigger<*>> = CRITERIA_TRIGGERS
        .register("contract_complete") {
            ContractCompleteTrigger.INSTANCE
        }

    @JvmStatic
    fun register() {
        CRITERIA_TRIGGERS.register()
    }
}
