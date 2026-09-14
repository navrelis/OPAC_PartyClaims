package xaero.pac.teamclaims.network;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import xaero.pac.teamclaims.config.TeamConfig;

import java.util.UUID;

public class ClientPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void handleConfigSync(final TeamConfigSyncPayload payload, final IPayloadContext context) {
        final TeamConfig config;
        try { config = payload.toTeamConfig(); }
        catch (Exception e) { LOGGER.error("Failed to deserialize team config sync payload", e); return; }
        context.enqueueWork(() -> {
            try { ClientTeamConfigCache.onConfigReceived(config); }
            catch (Exception e) { LOGGER.error("Failed to apply team config sync", e); }
        });
    }

    public static void handleConfigRemoved(final TeamConfigRemovedPayload payload, final IPayloadContext context) {
        final UUID partyId = payload.partyId();
        context.enqueueWork(() -> {
            try { ClientTeamConfigCache.onConfigRemoved(partyId); }
            catch (Exception e) { LOGGER.error("Failed to handle team config removal", e); }
        });
    }
}
