package xaero.pac.teamclaims;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.claims.player.IPlayerChunkClaim;
import xaero.pac.common.platform.Services;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.claims.forceload.ForceLoadTicketManager;

import java.util.*;

/**
 * Owns the chunk tickets of team forceloads.
 * <p>
 * Tickets go through OPAC's loader abstraction ({@code IServerChunkCacheAccess}) with {@code forceTicks}, exactly
 * like OPAC's own forceloads ({@link ForceLoadTicketManager}), so forceloaded team chunks get natural spawning and
 * random ticks without a player nearby on every loader, and dimensions holding team tickets keep ticking entities
 * while empty ({@link #keepLevelsTicking()}).
 * <p>
 * Balance: a ticket is only ever added for a chunk this handler doesn't hold yet and only removed for one it holds
 * ({@link #heldTickets}), so adds and removes always pair up. A team ticket is a different ticket type than OPAC's own,
 * so the two never cancel each other out. On Fabric, however, the "force ticks" state is a plain per-chunk set
 * shared by every ticket using it: removing either ticket clears it for the other one. Both directions are repaired:
 * {@link #removeForceLoad} re-asserts OPAC's ticket if OPAC still holds one for the chunk, and
 * {@link #onOpacTicketRemoved} re-asserts the team ticket. Re-adding an existing ticket is a no-op for the ticket
 * itself on every loader, so this never unbalances anything.
 * <p>
 * Server-thread confined.
 */
public class TeamForceLoadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final TicketType<ChunkPos> TEAM_TICKET = TicketType.create(
            OpenPartiesAndClaims.MOD_ID + ":team_forced",
            Comparator.comparingLong(ChunkPos::toLong)
    );
    private static final int TICKET_DISTANCE = 2;//same as OPAC's own forceload tickets

    private final MinecraftServer server;
    private final Set<UUID> activeTeams = new HashSet<>();
    /** Chunks (packed {@link ChunkPos#toLong()}) this handler currently holds a team ticket for, by dimension. */
    private final Map<ResourceLocation, LongSet> heldTickets = new HashMap<>();

    public TeamForceLoadHandler(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Adds the team ticket for a chunk, unless this handler already holds it.
     *
     * @return true if a ticket was added
     */
    public boolean addForceLoad(ResourceLocation dimension, int x, int z) {
        ServerLevel level = getLevel(dimension);
        if (level == null) return false;//e.g. a dimension that was removed from the server
        if (!heldTickets.computeIfAbsent(dimension, d -> new LongOpenHashSet()).add(ChunkPos.asLong(x, z))) return false;
        ChunkPos pos = new ChunkPos(x, z);
        Services.PLATFORM.getServerChunkCacheAccess().addRegionTicket(level.getChunkSource(), TEAM_TICKET, pos, TICKET_DISTANCE, pos, true);
        return true;
    }

    /**
     * Removes the team ticket for a chunk, if this handler holds it.
     *
     * @return true if a ticket was removed
     */
    public boolean removeForceLoad(ResourceLocation dimension, int x, int z) {
        LongSet held = heldTickets.get(dimension);
        if (held == null || !held.remove(ChunkPos.asLong(x, z))) return false;
        if (held.isEmpty()) heldTickets.remove(dimension);
        ServerLevel level = getLevel(dimension);
        if (level == null) return true;
        ChunkPos pos = new ChunkPos(x, z);
        Services.PLATFORM.getServerChunkCacheAccess().removeRegionTicket(level.getChunkSource(), TEAM_TICKET, pos, TICKET_DISTANCE, pos, true);
        restoreOpacForceTicks(level, dimension, pos);
        return true;
    }

    public boolean hasTicket(ResourceLocation dimension, int x, int z) {
        LongSet held = heldTickets.get(dimension);
        return held != null && held.contains(ChunkPos.asLong(x, z));
    }

    public int getHeldTicketCount() {
        int count = 0;
        for (LongSet held : heldTickets.values()) count += held.size();
        return count;
    }

    /**
     * OPAC just removed its own ticket for a chunk; if this handler holds a team ticket there, re-assert it so that
     * the chunk keeps its forced ticking state (see the class description).
     */
    public void onOpacTicketRemoved(ResourceLocation dimension, int x, int z) {
        if (!hasTicket(dimension, x, z)) return;
        ServerLevel level = getLevel(dimension);
        if (level == null) return;
        ChunkPos pos = new ChunkPos(x, z);
        Services.PLATFORM.getServerChunkCacheAccess().addRegionTicket(level.getChunkSource(), TEAM_TICKET, pos, TICKET_DISTANCE, pos, true);
    }

    /** Re-adds every held ticket of a dimension, e.g. after the claims of a whole dimension were reset. */
    public void reassertTickets(ResourceLocation dimension) {
        LongSet held = heldTickets.get(dimension);
        ServerLevel level = held == null ? null : getLevel(dimension);
        if (level == null) return;
        for (LongIterator it = held.iterator(); it.hasNext(); ) {
            ChunkPos pos = new ChunkPos(it.nextLong());
            Services.PLATFORM.getServerChunkCacheAccess().addRegionTicket(level.getChunkSource(), TEAM_TICKET, pos, TICKET_DISTANCE, pos, true);
        }
    }

    /**
     * Same as OPAC's {@code ServerCore.preServerLevelTick} does for its own forceloads: a dimension with team
     * forceload tickets keeps ticking entities even when no player is in it. Called once per server tick.
     */
    public void keepLevelsTicking() {
        if (heldTickets.isEmpty()) return;
        for (ResourceLocation dimension : heldTickets.keySet()) {
            ServerLevel level = getLevel(dimension);
            if (level != null) level.resetEmptyTime();
        }
    }

    /**
     * Removing our ticket also cleared the shared per-chunk "force ticks" state on Fabric. If OPAC still holds its
     * own ticket for the claim of this chunk, add it again: a no-op for the ticket, but it restores that state.
     */
    private void restoreOpacForceTicks(ServerLevel level, ResourceLocation dimension, ChunkPos pos) {
        IServerData<?, ?> serverData = ServerData.from(server);
        if (serverData == null) return;
        IPlayerChunkClaim claim = serverData.getServerClaimsManager().get(dimension, pos.x, pos.z);
        if (claim == null || !claim.isForceloadable()) return;
        ForceLoadTicketManager opacTickets = serverData.getForceLoadManager();
        if (!opacTickets.isTicketEnabled(dimension, claim.getPlayerId(), pos.x, pos.z)) return;
        Services.PLATFORM.getServerChunkCacheAccess().addRegionTicket(level.getChunkSource(),
                ForceLoadTicketManager.OPAC_TICKET, pos, TICKET_DISTANCE, pos, true);
    }

    private ServerLevel getLevel(ResourceLocation dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    public void markTeamActive(UUID partyId) { activeTeams.add(partyId); }
    public void markTeamInactive(UUID partyId) { activeTeams.remove(partyId); }
    public boolean isTeamActive(UUID partyId) { return activeTeams.contains(partyId); }

    public void onServerStarted() {
        activeTeams.clear();
    }

    /** Releases every held ticket. */
    public void onServerStopping() {
        int released = 0;
        for (Map.Entry<ResourceLocation, LongSet> entry : new ArrayList<>(heldTickets.entrySet())) {
            for (long packed : entry.getValue().toLongArray()) {
                ChunkPos pos = new ChunkPos(packed);
                if (removeForceLoad(entry.getKey(), pos.x, pos.z)) released++;
            }
        }
        heldTickets.clear();
        activeTeams.clear();
        if (released > 0) LOGGER.info("[TeamClaims] Released {} team forceload ticket(s)", released);
    }
}
