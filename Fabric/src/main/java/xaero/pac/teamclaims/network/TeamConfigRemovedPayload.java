package xaero.pac.teamclaims.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import xaero.pac.OpenPartiesAndClaims;

import java.util.UUID;

public record TeamConfigRemovedPayload(UUID partyId) implements CustomPacketPayload {

    public static final Type<TeamConfigRemovedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OpenPartiesAndClaims.MOD_ID, "team_config_removed")
    );

    public static final StreamCodec<ByteBuf, TeamConfigRemovedPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            TeamConfigRemovedPayload::partyId,
            TeamConfigRemovedPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
