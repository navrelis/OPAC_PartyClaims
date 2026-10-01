package xaero.pac.teamclaims;

import com.mojang.logging.LogUtils;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamForceLoadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final TicketType<ChunkPos> TEAM_TICKET = TicketType.create(
            OpenPartiesAndClaims.MOD_ID + ":team_forced",
            Comparator.comparingLong(ChunkPos::toLong)
    );

    private final MinecraftServer server;
    private final Set<UUID> activeTeams = ConcurrentHashMap.newKeySet();

    public TeamForceLoadHandler(MinecraftServer server) {
        this.server = server;
    }

    public void addForceLoad(ResourceLocation dimension, int x, int z) {
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimension);
        ServerLevel level = server.getLevel(levelKey);
        if (level != null) {
            ChunkPos chunkPos = new ChunkPos(x, z);
            level.getChunkSource().addRegionTicket(TEAM_TICKET, chunkPos, 2, chunkPos);
        }
    }

    public void removeForceLoad(ResourceLocation dimension, int x, int z) {
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimension);
        ServerLevel level = server.getLevel(levelKey);
        if (level != null) {
            ChunkPos chunkPos = new ChunkPos(x, z);
            level.getChunkSource().removeRegionTicket(TEAM_TICKET, chunkPos, 2, chunkPos);
        }
    }

    public void markTeamActive(UUID partyId) { activeTeams.add(partyId); }
    public void markTeamInactive(UUID partyId) { activeTeams.remove(partyId); }
    public boolean isTeamActive(UUID partyId) { return activeTeams.contains(partyId); }

    public void onServerStarted() { activeTeams.clear(); }

    public void onServerStopping() {
        TeamClaimManager manager = TeamClaimsInit.getClaimManager();
        if (manager == null) return;
        for (UUID partyId : new ArrayList<>(activeTeams)) {
            TeamClaimManager.TeamData teamData = manager.getTeamData(partyId);
            if (teamData != null) {
                for (TeamClaimManager.ClaimPos pos : teamData.forceLoadedChunks) {
                    removeForceLoad(pos.dimension, pos.x, pos.z);
                }
            }
        }
        activeTeams.clear();
    }
}
