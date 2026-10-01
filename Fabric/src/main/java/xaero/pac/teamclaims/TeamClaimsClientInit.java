package xaero.pac.teamclaims;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import xaero.pac.teamclaims.network.ClientPayloadHandler;
import xaero.pac.teamclaims.network.ClientTeamConfigCache;
import xaero.pac.teamclaims.network.TeamConfigRemovedPayload;
import xaero.pac.teamclaims.network.TeamConfigSyncPayload;

/**
 * Client-only Team Claims wiring: payload receivers and disconnect cleanup.
 * <p>
 * This must only ever be invoked from the physical client entrypoint
 * ({@code OpenPartiesAndClaimsFabric#onInitializeClient()}), never from
 * {@link TeamClaimsInit#init()} (which also runs on a dedicated server). {@link ClientPayloadHandler}
 * and the {@code net.fabricmc.fabric.api.client.networking.v1} classes referenced here must never be
 * reachable from common/server code so a dedicated server never classloads them.
 */
public class TeamClaimsClientInit {

    private static volatile boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        ClientPlayNetworking.registerGlobalReceiver(TeamConfigSyncPayload.TYPE, ClientPayloadHandler::handleConfigSync);
        ClientPlayNetworking.registerGlobalReceiver(TeamConfigRemovedPayload.TYPE, ClientPayloadHandler::handleConfigRemoved);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientTeamConfigCache.clear());
    }
}
