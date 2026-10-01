package xaero.pac.teamclaims.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.teamclaims.config.TeamConfig;

public record TeamConfigSyncPayload(String jsonData) implements CustomPacketPayload {

    public static final Type<TeamConfigSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OpenPartiesAndClaims.MOD_ID, "team_config_sync")
    );

    public static final StreamCodec<ByteBuf, TeamConfigSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144),
            TeamConfigSyncPayload::jsonData,
            TeamConfigSyncPayload::new
    );

    public TeamConfigSyncPayload(TeamConfig config) {
        this(config.toJson().toString());
    }

    public TeamConfig toTeamConfig() {
        JsonObject json = JsonParser.parseString(jsonData).getAsJsonObject();
        return TeamConfig.fromJson(json);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
