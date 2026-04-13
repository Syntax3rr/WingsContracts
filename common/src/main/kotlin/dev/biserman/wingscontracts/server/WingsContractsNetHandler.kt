package dev.biserman.wingscontracts.server

import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.networking.NetworkManager
import net.minecraft.server.level.ServerLevel

object WingsContractsNetHandler {
    val SYNC_AVAILABLE_CONTRACTS = NetworkManager.registerReceiver(
        NetworkManager.Side.S2C,
        SyncAvailableContractsPacket.PACKET_ID,
        SyncAvailableContractsPacket.PACKET_CODEC,
        SyncAvailableContractsPacket::handle
    )
    val CREATE_BOUND_CONTRACTS = NetworkManager.registerReceiver(
        NetworkManager.Side.C2S,
        CreateBoundContractsPacket.PACKET_ID,
        CreateBoundContractsPacket.PACKET_CODEC,
        CreateBoundContractsPacket::handle
    )

    fun init() {
        PlayerEvent.PLAYER_JOIN.register { player ->
            NetworkManager.sendToPlayer(player, SyncAvailableContractsPacket(player.level() as ServerLevel))
        }
    }
}