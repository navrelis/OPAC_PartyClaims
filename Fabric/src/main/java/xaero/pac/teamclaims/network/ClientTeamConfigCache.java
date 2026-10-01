package xaero.pac.teamclaims.network;

import xaero.pac.teamclaims.config.TeamConfig;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientTeamConfigCache {

    @Nullable
    private static volatile TeamConfig currentConfig = null;
    private static final ConcurrentHashMap<UUID, TeamConfig> configsByParty = new ConcurrentHashMap<>();

    public static void onConfigReceived(TeamConfig config) {
        currentConfig = config;
        configsByParty.put(config.getPartyId(), config);
    }

    public static void onConfigRemoved(UUID partyId) {
        configsByParty.remove(partyId);
        TeamConfig cfg = currentConfig;
        if (cfg != null && cfg.getPartyId().equals(partyId)) currentConfig = null;
    }

    @Nullable public static TeamConfig getCurrentConfig() { return currentConfig; }
    @Nullable public static TeamConfig getConfigForParty(UUID partyId) { return configsByParty.get(partyId); }
    public static boolean isInTeam() { return currentConfig != null; }

    public static void clear() {
        currentConfig = null;
        configsByParty.clear();
    }
}
