package xaero.pac.teamclaims.admin;

import net.minecraft.server.MinecraftServer;
import xaero.pac.common.packet.teamclaims.AdminTeamData;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.teamclaims.TeamClaimManager;
import xaero.pac.teamclaims.TeamClaimManager.TeamBudget;
import xaero.pac.teamclaims.TeamClaimsCommon;
import xaero.pac.teamclaims.config.TeamConfig;
import xaero.pac.teamclaims.config.TeamConfigManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The teams of the server as the admin menu and {@code /teamclaims bonus} see them. Server thread only. */
public final class AdminTeams {

    private AdminTeams() {}

    /** Every team (party with a team config) with its budget, sorted by name; empty while Team Claims is not active. */
    public static List<AdminTeamData> list(MinecraftServer server) {
        TeamClaimManager cm = TeamClaimsCommon.getClaimManager();
        TeamConfigManager tcm = TeamClaimsCommon.getTeamConfigManager();
        if (cm == null || tcm == null) return List.of();
        List<AdminTeamData> teams = new ArrayList<>();
        for (TeamConfig config : new ArrayList<>(tcm.getTeamConfigs())) {
            IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(config.getPartyId());
            TeamBudget budget = party == null ? null : cm.getTeamBudget(party.getId());
            if (budget == null) continue;
            teams.add(new AdminTeamData(party.getId(), nameOf(tcm, config, party), budget.memberCount(), budget.claims(),
                    budget.claimLimit(), budget.forceloads(), budget.forceloadLimit(), budget.bonusClaims(),
                    budget.bonusForceloads()));
        }
        teams.sort(Comparator.comparing((AdminTeamData team) -> team.name().toLowerCase(Locale.ROOT))
                .thenComparing(team -> team.partyId().toString()));
        return teams;
    }

    /** The name of a team as {@code /teamclaims info} shows it: the team name, else the party name. */
    public static String nameOf(TeamConfigManager tcm, TeamConfig config, IServerPartyAPI party) {
        String teamName = config.getTeamName();
        return teamName == null || teamName.isBlank() ? tcm.resolvePartyName(party) : teamName;
    }

    /**
     * The teams an admin means with {@code input}: the one with that party id, else every team with that name (any
     * case). Empty if none, more than one if the name is not unique.
     */
    public static List<AdminTeamData> resolve(MinecraftServer server, String input) {
        List<AdminTeamData> teams = list(server);
        UUID id = parseUuid(input);
        if (id != null) {
            List<AdminTeamData> byId = teams.stream().filter(team -> team.partyId().equals(id)).toList();
            if (!byId.isEmpty()) return byId;
        }
        return teams.stream().filter(team -> team.name().equalsIgnoreCase(input.trim())).toList();
    }

    /** The team of one party, null if it has none. */
    @Nullable
    public static AdminTeamData get(MinecraftServer server, UUID partyId) {
        return list(server).stream().filter(team -> team.partyId().equals(partyId)).findFirst().orElse(null);
    }

    @Nullable
    static UUID parseUuid(String input) {
        String trimmed = input.trim();
        if (trimmed.length() != 36) return null;
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
