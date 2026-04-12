package dev.biserman.wingscontracts.server

import dev.architectury.networking.NetworkManager
import dev.biserman.wingscontracts.WingsContractsMod.prefix
import dev.biserman.wingscontracts.data.ContractSavedData
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel


class SyncAvailableContractsPacket(val tag: CompoundTag) : CustomPacketPayload {
    constructor(level: ServerLevel) : this(ContractSavedData.get(level).save(CompoundTag(), level.registryAccess()))

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        val ID = prefix("sync_available_contracts")
        val PACKET_ID = CustomPacketPayload.Type<SyncAvailableContractsPacket>(ID)
        val PACKET_CODEC: StreamCodec<ByteBuf, SyncAvailableContractsPacket> =
            ByteBufCodecs.COMPOUND_TAG.map(::SyncAvailableContractsPacket) { it.tag }

        fun handle(payload: SyncAvailableContractsPacket, context: NetworkManager.PacketContext) {
            ContractSavedData.set(
                Minecraft.getInstance().level ?: return,
                ContractSavedData.load(payload.tag, context.player.level().registryAccess())
            )
        }
    }
}