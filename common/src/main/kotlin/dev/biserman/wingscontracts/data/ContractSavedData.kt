package dev.biserman.wingscontracts.data

import dev.architectury.networking.NetworkManager
import dev.biserman.wingscontracts.WingsContractsMod
import dev.biserman.wingscontracts.config.DenominatedCurrenciesHandler
import dev.biserman.wingscontracts.config.ModConfig
import dev.biserman.wingscontracts.container.AvailableContractsContainer
import dev.biserman.wingscontracts.scoreboard.ScoreboardHandler
import dev.biserman.wingscontracts.server.SyncAvailableContractsPacket
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import java.util.*
import kotlin.math.min

class ContractSavedData : SavedData() {
    val container = AvailableContractsContainer()
    val remainingPicks = mutableMapOf<String, Int>()
    var currentCycleStart: Long = 0
    val nextCycleStart get() = currentCycleStart + ModConfig.SERVER.abyssalContractsPoolRefreshPeriodMs.get()

    val rarityThresholds by lazy { ModConfig.SERVER.rarityThresholdsString.get().split(",").map { it.toInt() } }
    val currencyHandler = DenominatedCurrenciesHandler()
    val generator by lazy { AbyssalContractGenerator(this) }

    override fun save(compoundTag: CompoundTag, provider: HolderLookup.Provider): CompoundTag {
        val contractListTag = CompoundTag()
        ContainerHelper.saveAllItems(contractListTag, container.items, provider)
        val remainingPicksTag = CompoundTag()
        remainingPicks.forEach { userId, picks ->
            remainingPicksTag.putInt(userId, picks)
        }

        compoundTag.put(REMAINING_PICKS, remainingPicksTag)
        compoundTag.put(CONTRACT_LIST, contractListTag)
        compoundTag.putLong(CURRENT_CYCLE_START, currentCycleStart)
        return compoundTag
    }

    fun serverTick(level: ServerLevel) {
        if (ModConfig.SERVER.abyssalContractsPoolRefreshPeriodMs.get() <= 0) {
            return
        }

        val cyclesPassed =
            (System.currentTimeMillis() - currentCycleStart) / ModConfig.SERVER.abyssalContractsPoolRefreshPeriodMs.get()

        if (cyclesPassed <= 0) {
            return
        }

        WingsContractsMod.LOGGER.info("starting new cycle $currentCycleStart")
        currentCycleStart += cyclesPassed * ModConfig.SERVER.abyssalContractsPoolRefreshPeriodMs.get()
        remainingPicks.keys.forEach {
            remainingPicks[it] = min(
                (remainingPicks[it] ?: 0) + ModConfig.SERVER.abyssalContractsPoolPicks.get(),
                ModConfig.SERVER.abyssalContractsPoolPicksCap.get()
            )
        }

        val playersToShow = ModConfig.SERVER.announceCycleLeaderboard.get()
        if (playersToShow != 0) {
            ScoreboardHandler.announceTopScores(level, playersToShow)
        }
        ScoreboardHandler.resetPeriodic(level)

        refresh(level)
    }

    fun clear(level: ServerLevel) {
        container.clearContent()
        NetworkManager.sendToPlayers(level.players(), SyncAvailableContractsPacket(level))
    }

    fun refresh(level: ServerLevel) {
        container.clearContent()
        for (i in 0..<container.containerSize) {
            container.items[i] = generator.generateContract(ContractDataReloadListener.randomTag()).createItem()
        }
        LoadedContracts.clear()
        NetworkManager.sendToPlayers(level.players(), SyncAvailableContractsPacket(level))
    }


    companion object {
        const val CONTRACT_LIST = "contractList"
        const val REMAINING_PICKS = "remainingPicks"
        const val CURRENT_CYCLE_START = "currentCycleStart"
        const val IDENTIFIER = "${WingsContractsMod.MOD_ID}_world_data"

        val FALLBACK_REWARD = RewardBagEntry(ItemStack(Items.EMERALD, 1), 1.0, 1)

        val random = Random()

        var fakeData = ContractSavedData()

        fun load(nbt: CompoundTag, provider: HolderLookup.Provider): ContractSavedData {
            val availableContracts = ContractSavedData()
            ContainerHelper.loadAllItems(nbt.getCompound(CONTRACT_LIST), availableContracts.container.items, provider)
            val remainingPicksTag = nbt.getCompound(REMAINING_PICKS)
            remainingPicksTag.allKeys.forEach { key ->
                availableContracts.remainingPicks[key] = remainingPicksTag.getInt(key)
            }
            availableContracts.currentCycleStart = nbt.getLong(CURRENT_CYCLE_START)
            return availableContracts
        }

        fun set(world: Level, data: ContractSavedData) {
            fakeData = data
            if (world !is ServerLevel) {
                return
            }

            world.server.getLevel(Level.OVERWORLD)?.dataStorage?.set(
                IDENTIFIER, data
            )
        }

        fun get(world: Level): ContractSavedData {
            if (world !is ServerLevel) {
                return fakeData
            }

            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            val data = world.server.getLevel(Level.OVERWORLD)?.dataStorage?.computeIfAbsent(
                Factory(::ContractSavedData, ContractSavedData::load, null), IDENTIFIER
            ) ?: fakeData

            return data
        }

        fun remainingPicks(player: Player): Int {
            val remainingPicksMap = get(player.level()).remainingPicks
            val id = player.uuid.toString()
            return remainingPicksMap.computeIfAbsent(id) { ModConfig.SERVER.abyssalContractsPoolPicks.get() }
        }

        fun setRemainingPicks(player: Player, picks: Int, update: Boolean = false) {
            val remainingPicksMap = get(player.level()).remainingPicks
            val id = player.uuid.toString()
            remainingPicksMap[id] = picks

            val level = player.level()
            if (update && level is ServerLevel) {
                NetworkManager.sendToPlayers(level.players(), SyncAvailableContractsPacket(level))
            }
        }

        fun addRemainingPicks(player: Player, picks: Int, update: Boolean = false) {
            val remainingPicksMap = get(player.level()).remainingPicks
            val id = player.uuid.toString()
            remainingPicksMap[id] = (remainingPicksMap[id] ?: 0) + picks

            val level = player.level()
            if (update && level is ServerLevel) {
                NetworkManager.sendToPlayers(level.players(), SyncAvailableContractsPacket(level))
            }
        }
    }
}